package pl.opole.edziennik

/**
 * USOS_CONSUMER_KEY/SECRET NIE są tu ani nigdzie indziej w kodzie wpisane —
 * użytkownik podaje je sam przy pierwszym uruchomieniu appki (patrz
 * `ui/setup/SetupScreen.kt`, zapisywane trwale przez `CredentialsStore`).
 * Dzięki temu ten plik i cały skompilowany APK można bezpiecznie
 * publikować — nawet zdekompilowany, nie ujawnia żadnego sekretu.
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
    const val OAUTH_CALLBACK_URL = "edziennik://oauth-callback"
    const val OAUTH_SCOPES = "grades|studies|other_emails|payments|offline_access"
}
