package pl.opole.edziennik.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.opole.edziennik.Config
import pl.opole.edziennik.MainActivity
import pl.opole.edziennik.oauth.CredentialsStore
import pl.opole.edziennik.R
import pl.opole.edziennik.data.NotificationEvent
import pl.opole.edziennik.data.NotificationHistoryStore
import pl.opole.edziennik.data.RawGrade
import pl.opole.edziennik.data.SessionEntry
import pl.opole.edziennik.data.SyncStateStore
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.oauth.TokenStore
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Zadanie cykliczne (co 12h, patrz `SyncScheduler`) — sprawdza plan na
 * najbliższe 14 dni i wszystkie oceny, porównuje z ostatnim znanym stanem
 * (`SyncStateStore`) i wysyła jedno zbiorcze powiadomienie, jeśli coś się
 * zmieniło. Działa niezależnie od tego, czy appka jest otwarta — dlatego
 * samodzielnie odtwarza klienta USOS z zapisanego tokenu, zamiast korzystać
 * z instancji trzymanych w `MainActivity`.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val credentials = TokenStore(applicationContext).load()
            ?: return@withContext Result.success() // nikt nie jest zalogowany — nic do zrobienia
        val consumerCredentials = CredentialsStore(applicationContext).load()
            ?: return@withContext Result.success() // klucz USOS jeszcze nie skonfigurowany — nic do zrobienia

        val apiClient = UsosApiClient(Config.USOS_BASE_URL)
        apiClient.setConsumerCredentials(consumerCredentials.consumerKey, consumerCredentials.consumerSecret)
        apiClient.accessToken = credentials.accessToken
        apiClient.accessTokenSecret = credentials.accessTokenSecret

        val repository = UsosRepository(apiClient, applicationContext.filesDir)
        val stateStore = SyncStateStore(File(applicationContext.filesDir, "sync_state"))
        val historyStore = NotificationHistoryStore(File(applicationContext.filesDir, "notification_history"))

        val events = mutableListOf<NotificationEvent>()
        checkSchedule(repository, stateStore, events)
        checkGrades(repository, stateStore, events)

        if (events.isNotEmpty()) {
            historyStore.addAll(events)
            postNotification(events)
        }

        Result.success()
    }

    private suspend fun checkSchedule(
        repository: UsosRepository,
        stateStore: SyncStateStore,
        events: MutableList<NotificationEvent>,
    ) {
        val today = LocalDate.now()
        val days = repository.fetchSchedule(today, today.plusDays(13), forceRefresh = true).getOrNull() ?: return

        val freshKeys = days.flatMap { day -> day.entries.map(::scheduleKey) }.toSet()
        val oldKeys = stateStore.readScheduleSnapshot()

        // Pierwsze uruchomienie (brak wcześniejszego stanu) tylko zapisuje
        // punkt odniesienia — bez tego pierwsza kontrola zgłosiłaby "zmianę"
        // dla całego planu naraz.
        if (oldKeys.isNotEmpty()) {
            val changed = (freshKeys - oldKeys).size + (oldKeys - freshKeys).size
            if (changed > 0) {
                events.add(
                    NotificationEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        type = "schedule",
                        message = "Wykryto $changed zmian(y) w planie zajęć (najbliższe 14 dni).",
                    ),
                )
            }
        }
        stateStore.writeScheduleSnapshot(freshKeys)
    }

    private suspend fun checkGrades(
        repository: UsosRepository,
        stateStore: SyncStateStore,
        events: MutableList<NotificationEvent>,
    ) {
        val grades = repository.fetchAllGrades(forceRefresh = true).getOrNull() ?: return

        val freshKeys = grades.map(::gradeKey).toSet()
        val oldKeys = stateStore.readGradesSnapshot()

        if (oldKeys.isNotEmpty()) {
            val newGrades = grades.filter { gradeKey(it) !in oldKeys }
            if (newGrades.isNotEmpty()) {
                val summary = newGrades.take(3).joinToString(", ") { "${it.courseName}: ${it.valueSymbol ?: "?"}" }
                val extra = if (newGrades.size > 3) " i ${newGrades.size - 3} więcej" else ""
                events.add(
                    NotificationEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        type = "grade",
                        message = "Nowa ocena: $summary$extra",
                    ),
                )
            }
        }
        stateStore.writeGradesSnapshot(freshKeys)
    }

    private fun scheduleKey(entry: SessionEntry): String =
        "${entry.startTime}|${entry.endTime}|${entry.displayName}|${entry.buildingName}|${entry.roomNumber}|${entry.lecturersDisplay}"

    private fun gradeKey(g: RawGrade): String =
        "${g.courseId}|${g.unitId}|${g.examId}|${g.dateModified}|${g.valueSymbol}"

    private fun postNotification(events: List<NotificationEvent>) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Aktualizacje e-dziennika", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        // Bez zgody na powiadomienia (Android 13+) zdarzenia i tak trafiły
        // już do historii — użytkownik zobaczy je w zakładce "Powiadomienia".
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val scheduleChanges = events.count { it.type == "schedule" }
        val gradeChanges = events.count { it.type == "grade" }
        val parts = mutableListOf<String>()
        if (scheduleChanges > 0) parts.add("$scheduleChanges zmian(y) w planie")
        if (gradeChanges > 0) parts.add("$gradeChanges nowa ocena/oceny")

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("e-dziennik — nowe zmiany")
            .setContentText(parts.joinToString(", "))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "edziennik_updates"
        private const val NOTIFICATION_ID = 1001
    }
}
