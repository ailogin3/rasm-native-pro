package com.example.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class AppVersionInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateMessage: String?,
    val updateUrl: String?
)

/**
 * Firestore equivalent of the Supabase app_version table used in the
 * sanghamconnect app. Reads a single document:
 *
 *   Collection: app_config
 *   Document:   version
 *   Fields:     latestVersionCode (number), latestVersionName (string),
 *               updateMessage (string), updateUrl (string)
 *
 * To disable the update banner temporarily, set latestVersionCode to 0
 * (or any value <= the installed app's version code) in that document --
 * no rebuild needed. Restore the real version code to re-enable it.
 *
 * Firestore rule needed (public read, no write from the app):
 *   match /app_config/{doc} {
 *     allow read: if true;
 *     allow write: if false;
 *   }
 */
object FirebaseAppVersionApi {

    /**
     * Fetches the latest published version info. Returns null on any
     * failure (missing doc, network error, permission denied) so callers
     * can simply skip showing the update banner rather than crash.
     */
    suspend fun fetchLatestVersion(): AppVersionInfo? {
        return try {
            val doc = FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("version")
                .get()
                .await()

            if (!doc.exists()) return null

            AppVersionInfo(
                latestVersionCode = doc.getLong("latestVersionCode")?.toInt() ?: 0,
                latestVersionName = doc.getString("latestVersionName") ?: "",
                updateMessage = doc.getString("updateMessage"),
                updateUrl = doc.getString("updateUrl")
            )
        } catch (e: Exception) {
            null
        }
    }
}
