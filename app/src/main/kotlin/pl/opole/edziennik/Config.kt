package pl.opole.edziennik

/**
 * USOS_CONSUMER_KEY/SECRET NIE są tu wpisane wprost — czytamy je z
 * BuildConfig, wygenerowanego przez Gradle na podstawie `local.properties`
 * (plik zignorowany przez git, patrz `native_app/README.md`). Dzięki temu
 * ten plik można bezpiecznie trzymać w repozytorium, nawet publicznym.
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
    val USOS_CONSUMER_KEY: String = BuildConfig.USOS_CONSUMER_KEY
    val USOS_CONSUMER_SECRET: String = BuildConfig.USOS_CONSUMER_SECRET
    const val OAUTH_CALLBACK_URL = "edziennik://oauth-callback"
    const val OAUTH_SCOPES = "grades|studies|other_emails|payments|offline_access"
}
