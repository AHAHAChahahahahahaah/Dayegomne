package com.example

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.activity.result.PickVisualMediaRequest
import com.example.util.CardBitmapGenerator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.CardEntity
import com.example.ui.GenerationState
import com.example.ui.MainViewModel
import com.example.ui.SaveState
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF07080B) // Immersive deep obsidian background
                ) {
                    CardGeneratorApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardGeneratorApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Forge, 1 = Collection

    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val baseBitmap by viewModel.baseBitmap.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val compiledCardBitmap by viewModel.compiledCardBitmap.collectAsStateWithLifecycle()
    val saveToGalleryState by viewModel.saveToGalleryState.collectAsStateWithLifecycle()
    val savedCards by viewModel.savedCards.collectAsStateWithLifecycle()

    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    var showSettingsDialog by remember { mutableStateOf(false) }

    var selectedCardForDetails by remember { mutableStateOf<CardEntity?>(null) }

    // Observers for alerts & toasts
    LaunchedEffect(saveToGalleryState) {
        when (saveToGalleryState) {
            is SaveState.Success -> {
                Toast.makeText(context, "Карточка успешно сохранена в галерею! 🎉", Toast.LENGTH_LONG).show()
            }
            is SaveState.Error -> {
                Toast.makeText(context, "Ошибка сохранения: ${(saveToGalleryState as SaveState.Error).message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    if (showSettingsDialog) {
        var tempKey by remember { mutableStateOf(customApiKey) }
        var tempModel by remember { mutableStateOf(selectedModel) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    "Настройки подключения",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Если стандартный ключ возвращает ошибку 403, укажите свой собственный API-ключ Gemini.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("Свой API Ключ Gemini", color = Color.Gray) },
                        placeholder = { Text("AIzaSy... или AQ...", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFECC94B),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Выберите модель ИИ:",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 14.sp
                    )

                    val models = listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro")
                    models.forEach { modelName ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempModel = modelName }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = tempModel == modelName,
                                onClick = { tempModel = modelName },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFECC94B),
                                    unselectedColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = modelName + if (modelName == "gemini-3.5-flash") " (Рекомендуется)" else "",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateCustomApiKey(tempKey)
                        viewModel.updateSelectedModel(tempModel)
                        showSettingsDialog = false
                        Toast.makeText(context, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Сохранить", color = Color(0xFFECC94B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("Отмена", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B),
            textContentColor = Color.White
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFE9C46A),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "КУЗНИЦА ИИ КАРТ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = Color(0xFFECC94B)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0C0D12),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0C0D12),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Кузница") },
                    label = { Text("Выковать") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFECC94B),
                        selectedTextColor = Color(0xFFECC94B),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Коллекция") },
                    label = { Text("Коллекция") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFECC94B),
                        selectedTextColor = Color(0xFFECC94B),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0C0D12), Color(0xFF07080B))
                    )
                )
        ) {
            // Check if Gemini API key is missing
            val isApiKeyMissing = BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY"

            Column(modifier = Modifier.fillMaxSize()) {
                if (isApiKeyMissing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .shadow(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "⚠️ Ошибка конфигурации ИИ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Ключ API Gemini не задан! Пожалуйста, добавьте его в панели Secrets в Google AI Studio, чтобы кузница карт ожила.",
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                    },
                    label = "TabContent"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> ForgeScreen(viewModel)
                        1 -> CollectionScreen(
                            savedCards = savedCards,
                            onCardClicked = { selectedCardForDetails = it },
                            onDeleteClicked = { viewModel.deleteCard(it) }
                        )
                    }
                }
            }

            // Card Details Overlay Dialog
            selectedCardForDetails?.let { card ->
                CardDetailsDialog(
                    cardEntity = card,
                    viewModel = viewModel,
                    onDismiss = { selectedCardForDetails = null }
                )
            }
        }
    }
}

@Composable
fun ForgeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val baseBitmap by viewModel.baseBitmap.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val compiledCardBitmap by viewModel.compiledCardBitmap.collectAsStateWithLifecycle()
    val saveToGalleryState by viewModel.saveToGalleryState.collectAsStateWithLifecycle()

    // Camera launcher setup
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.tempCameraImageFile?.let { file ->
                viewModel.selectImage(Uri.fromFile(file))
            }
        }
    }

    // Gallery launcher setup
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectImage(uri)
        }
    }

    fun launchCamera() {
        val cacheFile = File(context.cacheDir, "forge_capture_${System.currentTimeMillis()}.jpg")
        viewModel.tempCameraImageFile = cacheFile
        val authority = "com.example.fileprovider"
        try {
            val uri = FileProvider.getUriForFile(context, authority, cacheFile)
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Не удалось открыть камеру: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (selectedImageUri == null) {
            // State A: Empty card silhouette
            EmptyForgeState(
                onCameraLaunch = { launchCamera() },
                onGalleryLaunch = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
        } else {
            // State B: Card has an image! Show preview or final card bitmap
            Card(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 400.dp)
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12141C)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (compiledCardBitmap != null) {
                        // Display high-res final trading card PNG
                        Image(
                            bitmap = compiledCardBitmap!!.asImageBitmap(),
                            contentDescription = "Твоя коллекционная карта",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        // Showing progress preview of user photo or dynamic loading
                        if (baseBitmap != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = baseBitmap!!.asImageBitmap(),
                                    contentDescription = "Оригинальное фото",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Overlay a dark tint and subtle text suggesting to Forge
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                )

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFECC94B),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Фото успешно заряжено!",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Нажмите «Выковать карту ИИ» ниже, чтобы раскрыть его скрытую редкость и характеристики.",
                                        color = Color.LightGray,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Loading base bitmap progress
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFECC94B))
                            }
                        }
                    }

                    // Magic/Mystic Loading state
                    if (generationState is GenerationState.Loading) {
                        MagicLoadingOverlay()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action bar depending on state
            BottomActionControl(
                generationState = generationState,
                saveState = saveToGalleryState,
                onForgeClicked = { viewModel.generateCard() },
                onSaveClicked = { viewModel.saveCardToGallery() },
                onResetClicked = { viewModel.resetActiveState() }
            )
        }
    }
}

@Composable
fun EmptyForgeState(onCameraLaunch: () -> Unit, onGalleryLaunch: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsing"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Card Silhouette placeholder
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 350.dp)
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .background(Color(0xFF0F111A), RoundedCornerShape(24.dp))
                .border(
                    width = 2.dp,
                    color = Color(0xFFECC94B).copy(alpha = alphaAnim),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Выберите фото для ковки",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ИИ проанализирует фото, применит рамку, определит редкость и выкует настоящую коллекционную карту",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large pick options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onCameraLaunch,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("camera_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сделать фото", color = Color.White, fontSize = 15.sp)
            }

            Button(
                onClick = onGalleryLaunch,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("gallery_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Из галереи", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun MagicLoadingOverlay() {
    val loadingPhrases = listOf(
        "🔮 Пробуждение ИИ-кузницы...",
        "👁️ Сканирование геометрии фото...",
        "🛡️ Балансировка атакующих сил...",
        "✨ Ковка сверкающего бевела...",
        "💎 Заклинание редкости карточки...",
        "🎨 Слияние слоев и рендеринг..."
    )

    var currentPhraseIdx by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            currentPhraseIdx = (currentPhraseIdx + 1) % loadingPhrases.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFFE9C46A),
                strokeWidth = 4.dp,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ИДЕТ КОВКА КАРТЫ",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = loadingPhrases[currentPhraseIdx],
                color = Color(0xFFE9C46A),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BottomActionControl(
    generationState: GenerationState,
    saveState: SaveState,
    onForgeClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onResetClicked: () -> Unit
) {
    AnimatedContent(
        targetState = generationState,
        label = "ActionControls"
    ) { state ->
        when (state) {
            is GenerationState.Idle -> {
                Button(
                    onClick = onForgeClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("forge_card_button")
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD69E2E) // Dark Gold
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выковать карту ИИ", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            is GenerationState.Loading -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Кузня работает...", fontSize = 16.sp)
                }
            }
            is GenerationState.Success -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onResetClicked,
                            modifier = Modifier
                                .weight(0.4f)
                                .height(56.dp)
                                .testTag("reset_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3748)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Сбросить", color = Color.White)
                        }

                        Button(
                            onClick = onSaveClicked,
                            enabled = saveState !is SaveState.Saving,
                            modifier = Modifier
                                .weight(0.6f)
                                .height(56.dp)
                                .testTag("save_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38A169)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (saveState is SaveState.Saving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("В галерею", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            is GenerationState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onResetClicked,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3748)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Сбросить")
                        }
                        Button(
                            onClick = onForgeClicked,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD69E2E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Повторить", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionScreen(
    savedCards: List<CardEntity>,
    onCardClicked: (CardEntity) -> Unit,
    onDeleteClicked: (CardEntity) -> Unit
) {
    if (savedCards.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Коллекция пуста",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Выкуйте свою первую карту во вкладке «Выковать»!",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Text(
                text = "ТВОЯ КОЛЛЕКЦИЯ (${savedCards.size})",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(savedCards) { card ->
                    CollectionCardItem(
                        card = card,
                        onClicked = { onCardClicked(card) },
                        onDelete = { onDeleteClicked(card) }
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionCardItem(
    card: CardEntity,
    onClicked: () -> Unit,
    onDelete: () -> Unit
) {
    val rarityColor = when (card.rarity.lowercase()) {
        "uncommon" -> Color(0xFF38A169)
        "rare" -> Color(0xFF3182CE)
        "epic" -> Color(0xFF805AD5)
        "legendary" -> Color(0xFFDD6B20)
        else -> Color(0xFF718096)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clickable { onClicked() }
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = rarityColor,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F111A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Load base image crop as background of preview item
            val file = File(card.imagePath)
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Dark bottom shadow gradient to read text clearly
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 150f
                        )
                    )
            )

            // Upper rarity indicator pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(rarityColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = card.rarity.uppercase(),
                    color = if (card.rarity.lowercase() == "legendary") Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // No gaming indicators for collector's cards

            // Card title & type
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = card.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.type,
                    color = rarityColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Delete Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 4.dp, y = 32.dp)
                    .size(24.dp)
                    .background(Color.Red.copy(alpha = 0.7f), CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailsDialog(
    cardEntity: CardEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var compiledBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Compile card on demand
    LaunchedEffect(cardEntity) {
        val file = File(cardEntity.imagePath)
        if (file.exists()) {
            val original = BitmapFactory.decodeFile(file.absolutePath)
            if (original != null) {
                val data = CardBitmapGenerator.CardData(
                    title = cardEntity.title,
                    description = cardEntity.description,
                    rarity = cardEntity.rarity,
                    type = cardEntity.type,
                    power = cardEntity.power,
                    shield = cardEntity.shield
                )
                compiledBitmap = CardBitmapGenerator.generateCard(context, original, data)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() } // Tap anywhere outside content to dismiss
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .align(Alignment.Center)
                    .clickable(enabled = false) {}, // Prevent dismiss on card tap
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 400.dp)
                        .aspectRatio(2f / 3f)
                        .shadow(24.dp, RoundedCornerShape(24.dp))
                ) {
                    if (compiledBitmap != null) {
                        Image(
                            bitmap = compiledBitmap!!.asImageBitmap(),
                            contentDescription = cardEntity.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(color = Color(0xFFECC94B), modifier = Modifier.align(Alignment.Center))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3748)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Закрыть", color = Color.White)
                    }

                    Button(
                        onClick = {
                            compiledBitmap?.let { bitmap ->
                                isSaving = true
                                viewModel.saveExistingCardToGallery(cardEntity) { success ->
                                    isSaving = false
                                    if (success) {
                                        Toast.makeText(context, "Успешно сохранено в галерею! 🎉", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = compiledBitmap != null && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38A169)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("В галерею", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
