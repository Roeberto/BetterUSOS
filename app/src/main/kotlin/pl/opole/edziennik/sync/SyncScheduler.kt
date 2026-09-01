package pl.opole.edziennik.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Włącza/wyłącza cykliczne sprawdzanie planu i ocen w tle (co 12h) — patrz
 * `SyncWorker`. Wywoływane z `AuthViewModel` przy logowaniu/wylogowaniu, żeby
 * zadanie działało tylko wtedy, gdy ktoś faktycznie jest zalogowany.
 */
object SyncScheduler {
    private const val WORK_NAME = "edziennik_sync"

    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS).build()
        // KEEP — jeśli zadanie już jest zaplanowane (np. appka ponownie
        // wykryła zalogowaną sesję przy starcie), nie zaczynamy odliczania
        // 12h od nowa.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
