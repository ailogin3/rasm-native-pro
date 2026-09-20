package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * PRO-PLAN BUILD.
 * Photos are uploaded to Firebase Cloud Storage and only the resulting download URL
 * is ever written to Firestore. There is no Firestore-embedded-base64 path in this
 * version — that free-tier approach has been removed entirely.
 *
 * Requires the Firebase project to be on the Blaze plan; Cloud Storage is not
 * reachable on Spark. If the project is still on Spark, uploads will fail with a
 * clear error (surfaced via the Result-style callback in each call site) rather
 * than silently falling back to base64.
 */
object ImageUtils {

    /**
     * Converts a photo reference (a Cloud Storage download URL, or any other
     * standard content/file URI) into a model Coil's AsyncImage can display directly.
     * Legacy raw/base64 strings (from data left over in Firestore before this
     * version) are still decoded here so old member photos don't just vanish —
     * but nothing in this build ever WRITES base64 again.
     */
    fun toImageModel(photo: String?): Any? {
        if (photo.isNullOrBlank()) return null
        val trimmed = photo.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("content://") || trimmed.startsWith("file://") ||
            trimmed.startsWith("android.resource://")
        ) {
            return trimmed
        }
        // Legacy fallback only — nothing new is ever saved in this format.
        return try {
            val base64Clean = if (trimmed.contains(",")) trimmed.substringAfter(",") else trimmed
            Base64.decode(base64Clean, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("ImageUtils", "Failed to decode legacy base64 photo", e)
            trimmed
        }
    }

    /**
     * Reads an image from a content Uri, resizes it down to [maxDimension] x [maxDimension],
     * accounts for Exif rotation, compresses it to JPEG, uploads it to Firebase Cloud Storage
     * under [storagePath], and returns the public download URL.
     *
     * Throws on failure (network error, Storage unavailable / project on Spark, bad image) —
     * callers should wrap in try/catch and surface the error to the user.
     */
    suspend fun uploadPhotoToStorage(
        uri: Uri,
        context: Context,
        storagePath: String,
        maxDimension: Int = 400
    ): String {
        val bytes = compressToJpeg(uri, context, maxDimension)
            ?: throw IllegalStateException("Could not read or process the selected image")

        val storage = FirebaseStorage.getInstance()
        val ref = storage.reference.child(storagePath)
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    /** Builds a unique storage path for a member's profile photo. */
    fun memberPhotoPath(memberKey: String): String =
        "members/${sanitize(memberKey)}/profile_${System.currentTimeMillis()}.jpg"

    /** Builds a unique storage path for a family member's photo, nested under the owning member. */
    fun familyPhotoPath(ownerKey: String, familyMemberId: String): String =
        "members/${sanitize(ownerKey)}/family/${sanitize(familyMemberId)}_${System.currentTimeMillis()}.jpg"

    /** Builds a unique storage path for a scanned meeting-minutes photo. */
    fun meetingPhotoPath(meetingId: String): String =
        "meetings/${sanitize(meetingId)}/${UUID.randomUUID()}.jpg"

    /**
     * Deletes the Cloud Storage object referenced by a previously-returned download URL.
     * Safe to call on legacy base64 strings or blank values — it just no-ops.
     */
    suspend fun deleteFromStorage(photoUrlOrLegacy: String?) {
        if (photoUrlOrLegacy.isNullOrBlank()) return
        if (!photoUrlOrLegacy.startsWith("https://") && !photoUrlOrLegacy.startsWith("http://")) return
        try {
            FirebaseStorage.getInstance().getReferenceFromUrl(photoUrlOrLegacy).delete().await()
        } catch (e: Exception) {
            // Best-effort cleanup only — don't fail the caller's main operation over this.
            Log.w("ImageUtils", "Could not delete storage file for $photoUrlOrLegacy", e)
        }
    }

    private fun sanitize(value: String): String =
        value.trim().ifBlank { "unknown" }.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun compressToJpeg(uri: Uri, context: Context, maxDimension: Int): ByteArray? {
        return try {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (e: Exception) {
                Log.w("ImageUtils", "Could not read EXIF orientation", e)
            }

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                Log.e("ImageUtils", "Invalid image bounds: ${origWidth}x${origHeight}")
                return null
            }

            var inSampleSize = 1
            while ((origWidth / (inSampleSize * 2)) >= maxDimension &&
                (origHeight / (inSampleSize * 2)) >= maxDimension
            ) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
            if (sampledBitmap == null) {
                Log.e("ImageUtils", "Failed to decode bitmap from URI: $uri")
                return null
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val orientedBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true)
            } else {
                sampledBitmap
            }

            val scale = minOf(
                maxDimension.toFloat() / orientedBitmap.width,
                maxDimension.toFloat() / orientedBitmap.height,
                1.0f
            )
            val finalWidth = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
            val finalHeight = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)

            val finalBitmap = if (finalWidth != orientedBitmap.width || finalHeight != orientedBitmap.height) {
                Bitmap.createScaledBitmap(orientedBitmap, finalWidth, finalHeight, true)
            } else {
                orientedBitmap
            }

            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
            outputStream.toByteArray()
        } catch (t: Throwable) {
            Log.e("ImageUtils", "Failed to process photo from URI $uri", t)
            null
        }
    }
}
