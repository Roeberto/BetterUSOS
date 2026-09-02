package pl.opole.edziennik

/**
 * Appka nigdy nie zna klucza/sekretu konsumenta USOS — wszystkie wywołania
 * API idą przez serwer podpisujący (Worker Cloudflare, patrz `proxy/`),
 * który dolicza podpis OAuth1 po swojej stronie i trzyma sekret wyłącznie
 * jako sekret Workera (`wrangler secret put`, nigdy w kodzie ani w gicie).
 * Dzięki temu każdy zbudowany APK — publiczny (CI) czy lokalny — jest
 * bezpieczny do publikacji nawet w publicznym repo: zdekompilowany, nie
 * ujawnia żadnego sekretu, i nie wymaga od użytkownika żadnej ręcznej
 * konfiguracji.
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
    /** Adres serwera podpisującego (Worker Cloudflare) — tu idą WSZYSTKIE
     * właściwe wywołania API USOS. Podmień na adres wypisany przez
     * `wrangler deploy` po wdrożeniu Workera (patrz `proxy/README.md`). */
    const val USOS_BASE_URL = "https://edziennik-usos-proxy.roboga03.workers.dev"

    /** Adres prawdziwego USOS — używany WYŁĄCZNIE do zbudowania linku do
     * strony autoryzacji logowania, otwieranej w przeglądarce (to zwykła,
     * niepodpisywana strona HTML, nie wywołanie API, więc nie idzie przez
     * Worker). */
    const val USOS_WEB_BASE_URL = "https://usosapps.po.edu.pl"

    const val OAUTH_CALLBACK_URL = "edziennik://oauth-callback"
    const val OAUTH_SCOPES = "grades|studies|other_emails|payments|offline_access"
}
