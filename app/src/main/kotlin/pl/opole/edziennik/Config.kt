package pl.opole.edziennik

/**
 * USOS_CONSUMER_KEY/SECRET domyślnie są puste — appka nie ma ich wbudowanych
 * na stałe, więc skompilowany APK (np. ten budowany automatycznie w CI przy
 * każdym commicie) można bezpiecznie publikować nawet w publicznym repo:
 * zdekompilowany, nie ujawnia żadnego sekretu.
 *
 * Jeśli jednak `local.properties` (plik zignorowany przez git, CI go nie
 * ma) zawiera te wartości, trafiają tu przez `BuildConfig` — appka
 * zbudowana NA TWOIM komputerze skonfiguruje się wtedy sama przy starcie,
 * bez pokazywania ekranu ustawień (patrz `MainActivity`). Brak tych
 * wartości (tak jak w CI) po prostu pokazuje `SetupScreen` — użytkownik
 * wpisuje je ręcznie, raz, i appka zapamiętuje je trwale na telefonie
 * (`CredentialsStore`).
 *
 * WAŻNE: OAUTH_CALLBACK_URL to niestandardowy schemat URI, pod który
 * przeglądarka wraca po autoryzacji w USOS (patrz intent-filter w
 * AndroidManifest.xml). Jeśli logowanie zwróci błąd o niedozwolonym
 * adresie zwrotnym, może być potrzebne dodanie go jako dozwolonego
 * callbacku w ustawieniach aplikacji na
 * https://usosapps.po.edu.pl/developers/ — dokładnie ten sam problem,
 * który rozwiązaliśmy dynamicznie po stronie serwera Flask, tu trzeba
 * będzie ustawić raz, na sztywno.
 */
object Config {
    const val USOS_BASE_URL = "https://usosapps.po.edu.pl"
    val DEFAULT_CONSUMER_KEY: String = BuildConfig.USOS_CONSUMER_KEY
    val DEFAULT_CONSUMER_SECRET: String = BuildConfig.USOS_CONSUMER_SECRET
    const val OAUTH_CALLBACK_URL = "edziennik://oauth-callback"
    const val OAUTH_SCOPES = "grades|studies|other_emails|payments|offline_access"
}
