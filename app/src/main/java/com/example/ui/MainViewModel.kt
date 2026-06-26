package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.GeminiCardResponse
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.CardDatabase
import com.example.data.CardEntity
import com.example.data.CardRepository
import com.example.util.CardBitmapGenerator
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface GenerationState {
    object Idle : GenerationState
    object Loading : GenerationState
    data class Success(val cardData: CardBitmapGenerator.CardData) : GenerationState
    data class Error(val message: String) : GenerationState
}

sealed interface SaveState {
    object Idle : SaveState
    object Saving : SaveState
    data class Success(val uri: Uri) : SaveState
    data class Error(val message: String) : SaveState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cardDatabase = CardDatabase.getDatabase(application)
    private val repository = CardRepository(cardDatabase.cardDao())

    private val prefs = application.getSharedPreferences("card_generator_prefs", android.content.Context.MODE_PRIVATE)

    private val _customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val customApiKey = _customApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString("selected_model", "gemini-3.5-flash") ?: "gemini-3.5-flash")
    val selectedModel = _selectedModel.asStateFlow()

    fun updateCustomApiKey(key: String) {
        prefs.edit().putString("custom_api_key", key.trim()).apply()
        _customApiKey.value = key.trim()
    }

    fun updateSelectedModel(model: String) {
        prefs.edit().putString("selected_model", model.trim()).apply()
        _selectedModel.value = model.trim()
    }

    // UI state for reactive list of saved cards
    val savedCards: StateFlow<List<CardEntity>> = repository.allCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current selected base photo URI
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri = _selectedImageUri.asStateFlow()

    // Loaded base bitmap
    private val _baseBitmap = MutableStateFlow<Bitmap?>(null)
    val baseBitmap = _baseBitmap.asStateFlow()

    // Card generation status
    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState = _generationState.asStateFlow()

    // Final compiled card bitmap (the 800x1200 trading card)
    private val _compiledCardBitmap = MutableStateFlow<Bitmap?>(null)
    val compiledCardBitmap = _compiledCardBitmap.asStateFlow()

    // Save to device gallery status
    private val _saveToGalleryState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveToGalleryState = _saveToGalleryState.asStateFlow()

    // Temporary camera image file
    var tempCameraImageFile: File? = null

    /**
     * Set the selected image URI and load the bitmap into memory.
     */
    fun selectImage(uri: Uri) {
        _selectedImageUri.value = uri
        _generationState.value = GenerationState.Idle
        _compiledCardBitmap.value = null
        _saveToGalleryState.value = SaveState.Idle

        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = ImageUtils.loadAndScaleBitmap(getApplication(), uri, 800)
            _baseBitmap.value = bitmap
        }
    }

    /**
     * Resets the active generation screen state.
     */
    fun resetActiveState() {
        _selectedImageUri.value = null
        _baseBitmap.value = null
        _generationState.value = GenerationState.Idle
        _compiledCardBitmap.value = null
        _saveToGalleryState.value = SaveState.Idle
    }

    /**
     * Calls Gemini 3.5 Flash to generate card content, then generates the card Bitmap
     * and automatically stores it in Room database.
     */
    fun generateCard() {
        val bitmap = _baseBitmap.value
        if (bitmap == null) {
            _generationState.value = GenerationState.Error("Сначала выберите или сделайте фото")
            return
        }

        // Check custom API key first, then fallback to BuildConfig.GEMINI_API_KEY
        val customKey = _customApiKey.value
        val defaultKey = BuildConfig.GEMINI_API_KEY
        val apiKey = if (customKey.isNotEmpty()) customKey else defaultKey
        
        val selected = _selectedModel.value
        val model = selected

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _generationState.value = GenerationState.Error(
                "Ключ API Gemini не настроен. Настройте его в приложении (иконка Шестерёнки вверху) или добавьте через Secrets в AI Studio."
            )
            return
        }

        _generationState.value = GenerationState.Loading
        _saveToGalleryState.value = SaveState.Idle

        viewModelScope.launch {
            try {
                // 1. Convert photo to Base64
                val base64Image = withContext(Dispatchers.IO) {
                    ImageUtils.bitmapToBase64(bitmap)
                }

                // 2. Build structured system prompt
                val promptText = "Анализируй это изображение и создай для него уникальную, творческую коллекционную (не игровую) карточку. " +
                        "Верни результат строго в формате JSON со следующими полями:\n" +
                        "1. 'title': короткое, оригинальное и креативное название карточки на русском языке (до 24 символов). " +
                        "Важно: Избегай избитых фэнтезийных шаблонов (таких как 'лорд', 'рыцарь', 'древний маг' и т. д.), " +
                        "если на фотографии нет явных фэнтезийных элементов. Название должно точно подходить под сюжет или юмор снимка.\n" +
                        "2. 'description': атмосферное, забавное или художественное описание карточки на русском языке, основанное на содержимом изображения (до 120 символов).\n" +
                        "3. 'rarity': редкость карточки. Выбери ровно одно из значений: 'Common', 'Uncommon', 'Rare', 'Epic', 'Legendary'. Подбирай редкость на основе необычности изображения.\n" +
                        "4. 'type': тип карточки на русском языке, описывающий её суть (например: 'Милый Обжора', 'Городской Романтик', 'Утренний Кофе', 'Верный Друг') — коротко, до 3 слов.\n\n" +
                        "Важно: Ответ должен состоять ТОЛЬКО из корректного JSON-объекта, без каких-либо дополнительных знаков, разметки ```json или другого текста."

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = promptText),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.85f
                    )
                )

                // 3. Request from Retrofit using dynamic model name
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(model, apiKey, request)
                }

                val rawJsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawJsonText == null) {
                    _generationState.value = GenerationState.Error("ИИ вернул пустой ответ. Попробуйте еще раз.")
                    return@launch
                }

                // Clean json markdown blocks if any
                val cleanJsonText = cleanJsonMarkdown(rawJsonText)

                // 4. Parse JSON
                val cardJson = withContext(Dispatchers.IO) {
                    try {
                        val adapter = RetrofitClient.moshiInstance.adapter(GeminiCardResponse::class.java)
                        adapter.fromJson(cleanJsonText)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }

                if (cardJson == null) {
                    _generationState.value = GenerationState.Error("Не удалось разобрать ответ ИИ. Текст ответа: $cleanJsonText")
                    return@launch
                }

                val cardData = CardBitmapGenerator.CardData(
                    title = cardJson.title,
                    description = cardJson.description,
                    rarity = cardJson.rarity,
                    type = cardJson.type,
                    power = cardJson.power,
                    shield = cardJson.shield
                )

                // 5. Generate compiled Card Bitmap
                val cardBitmap = withContext(Dispatchers.IO) {
                    CardBitmapGenerator.generateCard(getApplication(), bitmap, cardData)
                }

                _compiledCardBitmap.value = cardBitmap
                _generationState.value = GenerationState.Success(cardData)

                // 6. Save base photo internally, and save card metadata to DB
                withContext(Dispatchers.IO) {
                    val savedInternalPath = ImageUtils.saveToInternalStorage(getApplication(), bitmap)
                    if (savedInternalPath != null) {
                        val entity = CardEntity(
                            title = cardData.title,
                            description = cardData.description,
                            rarity = cardData.rarity,
                            imagePath = savedInternalPath,
                            power = cardData.power,
                            shield = cardData.shield,
                            type = cardData.type
                        )
                        repository.insertCard(entity)
                    }
                }

            } catch (e: retrofit2.HttpException) {
                e.printStackTrace()
                val errorBody = e.response()?.errorBody()?.string()
                val parsedMsg = try {
                    if (!errorBody.isNullOrEmpty()) {
                        // Extract "message" field if it is JSON error
                        val moshi = RetrofitClient.moshiInstance
                        val mapAdapter = moshi.adapter(Map::class.java)
                        val map = mapAdapter.fromJson(errorBody) as? Map<*, *>
                        val errorMap = map?.get("error") as? Map<*, *>
                        errorMap?.get("message")?.toString() ?: errorBody
                    } else {
                        "HTTP ${e.code()}"
                    }
                } catch (jsonEx: Exception) {
                    errorBody ?: "HTTP ${e.code()}"
                }
                _generationState.value = GenerationState.Error("Ошибка вызова Gemini API ($parsedMsg)")
            } catch (e: Exception) {
                e.printStackTrace()
                _generationState.value = GenerationState.Error("Ошибка вызова Gemini API: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Saves the currently active compiled card bitmap to the system gallery.
     */
    fun saveCardToGallery() {
        val bitmap = _compiledCardBitmap.value
        val state = _generationState.value
        if (bitmap == null || state !is GenerationState.Success) {
            _saveToGalleryState.value = SaveState.Error("Карточка еще не сгенерирована")
            return
        }

        _saveToGalleryState.value = SaveState.Saving

        viewModelScope.launch(Dispatchers.IO) {
            val savedUri = ImageUtils.saveCardToGallery(getApplication(), bitmap, state.cardData.title)
            if (savedUri != null) {
                _saveToGalleryState.value = SaveState.Success(savedUri)
            } else {
                _saveToGalleryState.value = SaveState.Error("Не удалось сохранить изображение в галерею")
            }
        }
    }

    /**
     * Re-generates and saves a previously saved card to the gallery.
     */
    fun saveExistingCardToGallery(entity: CardEntity, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(entity.imagePath)
                if (!file.exists()) {
                    onFinished(false)
                    return@launch
                }
                val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (originalBitmap == null) {
                    onFinished(false)
                    return@launch
                }
                val cardData = CardBitmapGenerator.CardData(
                    title = entity.title,
                    description = entity.description,
                    rarity = entity.rarity,
                    type = entity.type,
                    power = entity.power,
                    shield = entity.shield
                )
                val compiledBitmap = CardBitmapGenerator.generateCard(getApplication(), originalBitmap, cardData)
                val savedUri = ImageUtils.saveCardToGallery(getApplication(), compiledBitmap, entity.title)
                onFinished(savedUri != null)
            } catch (e: Exception) {
                e.printStackTrace()
                onFinished(false)
            }
        }
    }

    /**
     * Deletes a card from database and removes its privately saved base photo.
     */
    fun deleteCard(card: CardEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(card.imagePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.deleteCard(card)
        }
    }

    private fun cleanJsonMarkdown(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7)
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3)
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3)
        }
        return cleaned.trim()
    }
}
