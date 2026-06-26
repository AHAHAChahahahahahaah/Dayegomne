package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    /**
     * Loads a bitmap from a URI, scaling it down if it exceeds [maxDimension] to avoid OOM.
     */
    fun loadAndScaleBitmap(context: Context, uri: Uri, maxDimension: Int = 1024): Bitmap? {
        var inputStream: InputStream? = null
        try {
            // First decode bounds to inspect size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            var srcWidth = options.outWidth
            var srcHeight = options.outHeight

            if (srcWidth <= 0 || srcHeight <= 0) return null

            // Calculate sample size
            var sampleSize = 1
            while (srcWidth / 2 >= maxDimension || srcHeight / 2 >= maxDimension) {
                srcWidth /= 2
                srcHeight /= 2
                sampleSize *= 2
            }

            // Decode actual bitmap with sampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            inputStream = context.contentResolver.openInputStream(uri)
            val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return null
            inputStream?.close()

            // Correct orientation if needed
            return rotateImageIfRequired(context, decodedBitmap, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Checks EXIF tags of the selected image and rotates it if necessary.
     */
    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(selectedImage)
            if (inputStream == null) return img

            val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ExifInterface(inputStream)
            } else {
                return img // Fallback for old versions if unavailable
            }

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return img
            }

            val rotated = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
            if (rotated != img) {
                img.recycle()
            }
            return rotated
        } catch (e: Exception) {
            e.printStackTrace()
            return img
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Saves a bitmap to the app's internal private storage, returning the absolute file path.
     */
    fun saveToInternalStorage(context: Context, bitmap: Bitmap): String? {
        return try {
            val filename = "photo_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, filename)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a trading card bitmap to the user's public device gallery as a PNG image.
     */
    fun saveCardToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val resolver = context.contentResolver
        val filename = "TradingCard_${title.replace(" ", "_")}_${System.currentTimeMillis()}.png"

        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TradingCards")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        var uri: Uri? = null
        try {
            uri = resolver.insert(collection, imageDetails)
            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    imageDetails.clear()
                    imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, imageDetails, null, null)
                }
            }
            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            // If failed, clean up uri if created
            if (uri != null) {
                resolver.delete(uri, null, null)
            }
            return null
        }
    }

    /**
     * Convert bitmap to base64 string for sending to Gemini API.
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
