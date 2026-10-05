package pl.opole.edziennik.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Ściąga zaktualizowany APK (patrz `UpdateChecker`) do prywatnego cache'u
 * appki i odpala systemowy instalator — appka nigdy "nie instaluje się sama
 * po cichu" (Android poza Play Store tego nie pozwala bez bycia aplikacją
 * systemową/device-ownerem); user i tak musi potwierdzić jednym tapnięciem
 * standardowe okienko "Zainstalować aktualizację?".
 */
class UpdateInstaller(private val context: Context) {
    private val client = OkHttpClient()

    /** Zwraca komunikat błędu do pokazania userowi, albo `null` przy sukcesie
     * (sukces = odpalono instalator albo ekran zgody na nieznane źródła —
     * samo pobranie się powiodło). */
    suspend fun downloadAndInstall(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Nie udało się pobrać aktualizacji."
                val body = response.body ?: return@withContext "Pusta odpowiedź serwera."

                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "app-update.apk")
                body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }

                withContext(Dispatchers.Main) { launchInstaller(file) }
                null
            }
        } catch (e: Exception) {
            e.message ?: "Błąd podczas pobierania aktualizacji."
        }
    }

    private fun launchInstaller(file: File) {
        // Od Androida 8 (API 26) każda aplikacja musi dostać osobną zgodę
        // "zainstaluj z tego źródła" — bez niej system i tak pokazałby ten
        // sam ekran zamiast instalatora, więc sprawdzamy to sami i kierujemy
        // tam prosto. Po powrocie z Ustawień user musi tapnąć "Pobierz" raz
        // jeszcze — nie da się stąd automatycznie wznowić.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(settingsIntent)
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
