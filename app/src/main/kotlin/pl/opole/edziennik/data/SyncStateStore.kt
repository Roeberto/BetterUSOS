package pl.opole.edziennik.data

import org.json.JSONArray
import java.io.File

/**
 * Trwały zapis "ostatniego znanego stanu" planu i ocen — używany przez
 * `SyncWorker` do wykrywania zmian między kolejnymi cyklicznymi
 * sprawdzeniami w tle. To coś innego niż `DiskCache`: tamten cache jest
 * nadpisywany świeżymi danymi przy każdym odświeżeniu, więc nie nadaje się
 * do pytania "co się zmieniło od ostatniego razu" — potrzebny jest osobny,
 * stabilny punkt odniesienia.
 *
 * Przechowuje nie całe dane, tylko zbiór krótkich kluczy tożsamości wpisu
 * (patrz `scheduleKey`/`gradeKey` w SyncWorker) — wystarczy do wykrycia
 * "coś się zmieniło", bez duplikowania parsowania.
 */
class SyncStateStore(private val dir: File) {
    init {
        if (!dir.exists()) dir.mkdirs()
    }

    fun readScheduleSnapshot(): Set<String> = readSet("schedule.json")
    fun writeScheduleSnapshot(keys: Set<String>) = writeSet("schedule.json", keys)

    fun readGradesSnapshot(): Set<String> = readSet("grades.json")
    fun writeGradesSnapshot(keys: Set<String>) = writeSet("grades.json", keys)

    private fun readSet(name: String): Set<String> {
        val file = File(dir, name)
        if (!file.exists()) return emptySet()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun writeSet(name: String, keys: Set<String>) {
        runCatching {
            val arr = JSONArray()
            keys.forEach { arr.put(it) }
            File(dir, name).writeText(arr.toString())
        }
    }
}
