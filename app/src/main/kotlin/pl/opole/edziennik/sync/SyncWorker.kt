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
import pl.opole.edziennik.R
import pl.opole.edziennik.data.NotificationEvent
import pl.opole.edziennik.data.NotificationHistoryStore
import pl.opole.edziennik.data.RawGrade
import pl.opole.edziennik.data.SessionEntry
import pl.opole.edziennik.data.SyncStateStore
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.data.hm
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

        val apiClient = UsosApiClient(Config.USOS_BASE_URL)
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

        val freshByKey = days.flatMap { it.entries }.associateBy(::scheduleKey)
        val freshKeys = freshByKey.keys
        val oldKeys = stateStore.readScheduleSnapshot()

        // Pierwsze uruchomienie (brak wcześniejszego stanu) tylko zapisuje
        // punkt odniesienia — bez tego pierwsza kontrola zgłosiłaby "zmianę"
        // dla całego planu naraz.
        if (oldKeys.isNotEmpty()) {
            val addedKeys = freshKeys - oldKeys
            val removedKeys = oldKeys - freshKeys

            if (addedKeys.isNotEmpty() || removedKeys.isNotEmpty()) {
                // Dla dodanych zajęć mamy cały SessionEntry; dla zniknionych
                // tylko stary klucz (freshByKey go już nie zawiera) — ale
                // scheduleKey() jest budowany z pól rozdzielonych "|", więc
                // da się z niego odtworzyć te same dane z powrotem (patrz
                // scheduleSummaryFromKey()), bez trzymania osobnego, pełnego
                // zrzutu starego planu na dysku.
                val addedLines = addedKeys.sorted().mapNotNull { freshByKey[it] }
                    .map { "+ ${scheduleSummary(it)}" }
                val removedLines = removedKeys.sorted().map { "− ${scheduleSummaryFromKey(it)}" }

                val allLines = addedLines + removedLines
                val shown = allLines.take(MAX_SCHEDULE_DETAIL_LINES)
                val extra = if (allLines.size > shown.size) "\n… i ${allLines.size - shown.size} więcej" else ""

                events.add(
                    NotificationEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        type = "schedule",
                        message = "Zmiany w planie zajęć:\n${shown.joinToString("\n")}$extra",
                    ),
                )
            }
        }
        stateStore.writeScheduleSnapshot(freshKeys.toSet())
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

    /** Jeden czytelny wiersz zmiany w planie, np.
     * "2026-10-12 10:00–11:30: Matematyka I, bud. A 101". */
    private fun scheduleSummary(entry: SessionEntry): String =
        scheduleSummaryParts(entry.startTime, entry.endTime, entry.displayName, entry.buildingName, entry.roomNumber)

    /** To samo co `scheduleSummary()`, ale dla zniknionych zajęć, dla
     * których mamy już tylko stary `scheduleKey()` (nie cały `SessionEntry`)
     * — rozbija go z powrotem na te same pola, w tej samej kolejności, w
     * jakiej `scheduleKey()` je skleił. */
    private fun scheduleSummaryFromKey(key: String): String {
        val parts = key.split("|", limit = 6)
        return scheduleSummaryParts(
            startTime = parts.getOrElse(0) { "" },
            endTime = parts.getOrElse(1) { "" },
            displayName = parts.getOrElse(2) { "zajęcia" },
            buildingName = parts.getOrElse(3) { "" },
            roomNumber = parts.getOrElse(4) { "" },
        )
    }

    private fun scheduleSummaryParts(
        startTime: String,
        endTime: String,
        displayName: String,
        buildingName: String,
        roomNumber: String,
    ): String {
        val date = startTime.take(10)
        val time = "${hm(startTime)}–${hm(endTime)}"
        val place = listOfNotNull(buildingName.ifBlank { null }, roomNumber.ifBlank { null }).joinToString(" ")
        return buildString {
            append(date).append(' ').append(time).append(": ").append(displayName)
            if (place.isNotBlank()) append(", ").append(place)
        }
    }

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

        // Zwinięte powiadomienie pokazuje tylko liczby (mało miejsca na
        // pasku); po rozwinięciu (BigTextStyle) widać te same szczegółowe
        // linie, co w historii "Powiadomienia" w appce — co dokładnie się
        // zmieniło, a nie tylko "ile".
        val detailText = events.joinToString("\n\n") { it.message }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("e-dziennik — nowe zmiany")
            .setContentText(parts.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "edziennik_updates"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_SCHEDULE_DETAIL_LINES = 6
    }
}
