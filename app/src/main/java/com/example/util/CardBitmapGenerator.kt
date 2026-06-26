package com.example.util

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream

object CardBitmapGenerator {

    data class CardData(
        val title: String,
        val description: String,
        val rarity: String, // "Common", "Uncommon", "Rare", "Epic", "Legendary"
        val type: String,
        val power: Int,
        val shield: Int
    )

    // Colors
    private val BG_DARK = Color.parseColor("#0C0D12")
    private val CARD_HINT_COLOR_COMMON = Color.parseColor("#718096")
    private val CARD_HINT_COLOR_UNCOMMON = Color.parseColor("#38A169")
    private val CARD_HINT_COLOR_RARE = Color.parseColor("#3182CE")
    private val CARD_HINT_COLOR_EPIC = Color.parseColor("#805AD5")
    private val CARD_HINT_COLOR_LEGENDARY = Color.parseColor("#DD6B20")

    private val CARD_GLOW_COLOR_COMMON = Color.parseColor("#4A5568")
    private val CARD_GLOW_COLOR_UNCOMMON = Color.parseColor("#48BB78")
    private val CARD_GLOW_COLOR_RARE = Color.parseColor("#4299E1")
    private val CARD_GLOW_COLOR_EPIC = Color.parseColor("#9F7AEA")
    private val CARD_GLOW_COLOR_LEGENDARY = Color.parseColor("#ECC94B")

    fun generateCard(context: Context, sourceBitmap: Bitmap, data: CardData): Bitmap {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val rarity = data.rarity.lowercase().replaceFirstChar { it.uppercase() }
        val rarityColor = when (rarity) {
            "Uncommon" -> CARD_HINT_COLOR_UNCOMMON
            "Rare" -> CARD_HINT_COLOR_RARE
            "Epic" -> CARD_HINT_COLOR_EPIC
            "Legendary" -> CARD_HINT_COLOR_LEGENDARY
            else -> CARD_HINT_COLOR_COMMON
        }

        val glowColor = when (rarity) {
            "Uncommon" -> CARD_GLOW_COLOR_UNCOMMON
            "Rare" -> CARD_GLOW_COLOR_RARE
            "Epic" -> CARD_GLOW_COLOR_EPIC
            "Legendary" -> CARD_GLOW_COLOR_LEGENDARY
            else -> CARD_GLOW_COLOR_COMMON
        }

        // 1. Draw atmospheric radial background (glowing core)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = RadialGradient(
            width / 2f, height / 2f, width * 0.8f,
            intArrayOf(Color.argb(90, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)), BG_DARK),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw subtle background pattern / textures
        val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 10
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        for (i in 0..width step 40) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), patternPaint)
        }
        for (i in 0..height step 40) {
            canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), patternPaint)
        }

        // 3. Draw Outer Card Border with Bevel and Glow
        val cardRect = RectF(30f, 30f, width - 30f, height - 30f)
        val outerCornerRadius = 48f

        // Glow Layer
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            color = glowColor
            alpha = 150
            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.OUTER)
        }
        canvas.drawRoundRect(cardRect, outerCornerRadius, outerCornerRadius, glowPaint)

        // Solid Outer Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            shader = LinearGradient(
                30f, 30f, width - 30f, height - 30f,
                intArrayOf(rarityColor, Color.parseColor("#1A202C"), rarityColor),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, outerCornerRadius, outerCornerRadius, borderPaint)

        // Inner frame border (gold/bronze/silver highlights)
        val innerCardRect = RectF(42f, 42f, width - 42f, height - 42f)
        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
            alpha = 40
        }
        canvas.drawRoundRect(innerCardRect, outerCornerRadius - 10, outerCornerRadius - 10, innerBorderPaint)

        // 4. Crop and Draw user's Base Image
        val imageRect = RectF(60f, 160f, width - 60f, 700f)
        val imgCornerRadius = 24f

        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(imageRect, imgCornerRadius, imgCornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Draw the image, centered and scaled to fill the bounding box (Center Crop)
        val destRect = Rect(imageRect.left.toInt(), imageRect.top.toInt(), imageRect.right.toInt(), imageRect.bottom.toInt())
        val srcRect = calculateCenterCropRect(sourceBitmap, destRect.width(), destRect.height())
        canvas.drawBitmap(sourceBitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Draw dynamic linear overlay inside image (to blend it with the card theme)
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, imageRect.top, 0f, imageRect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(120, 12, 13, 18)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(imageRect, overlayPaint)
        canvas.restore()

        // Draw border around the cropped image
        val imgBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = rarityColor
        }
        canvas.drawRoundRect(imageRect, imgCornerRadius, imgCornerRadius, imgBorderPaint)

        // 5. Draw Title Capsule
        val titleRect = RectF(60f, 60f, width - 60f, 140f)
        val titleCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                60f, 60f, width - 60f, 60f,
                intArrayOf(Color.parseColor("#1E293B"), Color.parseColor("#0F172A")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(titleRect, 16f, 16f, titleCapPaint)

        val titleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = rarityColor
        }
        canvas.drawRoundRect(titleRect, 16f, 16f, titleBorderPaint)

        // Write Title text
        val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titleText = data.title
        val titleX = 85f
        val titleY = 110f
        canvas.drawText(titleText, titleX, titleY, titleTextPaint)

        // 6. Draw Rarity Badge/Ribbon at top right of title bar
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val badgeText = rarity.uppercase()
        val badgeWidth = badgeTextPaint.measureText(badgeText) + 30f
        val badgeRect = RectF(width - 75f - badgeWidth, 75f, width - 75f, 125f)

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rarityColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 8f, 8f, badgeBgPaint)
        canvas.drawText(badgeText, badgeRect.left + 15f, badgeRect.centerY() + 7f, badgeTextPaint)

        // 7. Card Type / Category Plate
        val typeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glowColor
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        val typeText = "[ ${data.type.uppercase()} ]"
        val typeWidth = typeTextPaint.measureText(typeText)
        canvas.drawText(typeText, (width - typeWidth) / 2f, 745f, typeTextPaint)

        // 8. Description / Flavor Text Box
        val descRect = RectF(60f, 775f, width - 60f, 1035f)
        val descBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#151821")
            alpha = 230
        }
        canvas.drawRoundRect(descRect, 20f, 20f, descBgPaint)

        val descBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.WHITE
            alpha = 30
        }
        canvas.drawRoundRect(descRect, 20f, 20f, descBorderPaint)

        // Write flavor text with wrapping
        val descTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val staticLayout = StaticLayout.Builder.obtain(
            data.description,
            0,
            data.description.length,
            descTextPaint,
            (descRect.width() - 40f).toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()

        canvas.save()
        // Center text vertically in the box
        val textHeight = staticLayout.height
        val startY = descRect.centerY() - (textHeight / 2f)
        canvas.translate(descRect.left + 20f, startY)
        staticLayout.draw(canvas)
        canvas.restore()

        // 9. Draw Stats Badge Bar at the footer
        val footerY = 1110f

        // POWER (Attack) circular badge on the left
        val powerBadgeX = 180f
        drawStatBadge(
            canvas = canvas,
            centerX = powerBadgeX,
            centerY = footerY,
            icon = "⚔️",
            label = "ATK",
            value = data.power.toString(),
            color = Color.parseColor("#EF4444")
        )

        // SHIELD (Defense) circular badge on the right
        val shieldBadgeX = 620f
        drawStatBadge(
            canvas = canvas,
            centerX = shieldBadgeX,
            centerY = footerY,
            icon = "🛡️",
            label = "DEF",
            value = data.shield.toString(),
            color = Color.parseColor("#3B82F6")
        )

        return bitmap
    }

    private fun drawStatBadge(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        icon: String,
        label: String,
        value: String,
        color: Int
    ) {
        val outerRadius = 55f
        val innerRadius = 50f

        // Outer rim
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            this.color = color
        }
        canvas.drawCircle(centerX, centerY, outerRadius, borderPaint)

        // Badge Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.parseColor("#1E293B")
        }
        canvas.drawCircle(centerX, centerY, innerRadius, bgPaint)

        // Emoji Icon
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(icon, centerX, centerY - 10f, iconPaint)

        // Value text
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(value, centerX, centerY + 28f, valuePaint)

        // Label small text
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor("#94A3B8")
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, centerX, centerY + 46f, labelPaint)
    }

    private fun calculateCenterCropRect(bitmap: Bitmap, viewWidth: Int, viewHeight: Int): Rect {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height

        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()

        var cropWidth = srcWidth
        var cropHeight = srcHeight
        var left = 0
        var top = 0

        if (srcRatio > viewRatio) {
            // Bitmap is wider than destination view - crop sides
            cropWidth = (srcHeight * viewRatio).toInt()
            left = (srcWidth - cropWidth) / 2
        } else {
            // Bitmap is taller than destination view - crop top/bottom
            cropHeight = (srcWidth / viewRatio).toInt()
            top = (srcHeight - cropHeight) / 2
        }

        return Rect(left, top, left + cropWidth, top + cropHeight)
    }
}
