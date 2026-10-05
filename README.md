# e-dziennik — natywna aplikacja Android

Kotlin + Jetpack Compose. Natywny odpowiednik aplikacji webowej z
`../app.py` — pokazuje plan zajęć, oceny, płatności i dane grup/osób z
USOS. Nie wymaga serwera Flask ani żadnej konfiguracji: po instalacji od
razu prowadzi do logowania przez USOS.

## Status

Projekt buduje się czysto (`./gradlew assembleDebug`) i był rozwijany
iteracyjnie na żywym urządzeniu — logowanie, cache, synchronizacja w tle i
wszystkie ekrany działały poprawnie w kolejnych testach. Mimo to traktuj
go jako projekt w rozwoju, nie skończony produkt: nowe przypadki brzegowe
wciąż mogą się pojawić.

## Jak to działa (i dlaczego APK jest bezpieczny)

Aplikacja **nigdy nie zna klucza ani sekretu konsumenta USOS**. Wszystkie
wywołania USOS API idą przez **serwer podpisujący** — Worker Cloudflare z
folderu [`proxy/`](proxy/):

```
aplikacja  ──►  Worker Cloudflare  ──►  usosapps.po.edu.pl
           ◄──  (dolicza podpis OAuth1)  ◄──
```

Worker trzyma klucz/sekret wyłącznie jako sekrety Cloudflare
(`wrangler secret put`) — nigdy w kodzie, w gicie ani w zbudowanym APK.
Aplikacja wysyła do Workera tylko token zalogowanego użytkownika
(`oauth_token`/`oauth_token_secret`); resztę OAuth1 (nonce, timestamp,
HMAC-SHA1) dolicza Worker i przekazuje żądanie dalej do USOS bez zmian.

Dzięki temu APK budowany automatycznie w CI można publikować nawet w
publicznym repo — zdekompilowany nie ujawnia żadnego sekretu — i nie ma w
nim żadnego ekranu wpisywania kluczy.

Strona autoryzacji logowania (`oauth/authorize`) to zwykła strona HTML na
prawdziwym USOS, więc otwiera się w przeglądarce bezpośrednio, z pominięciem
Workera (osobna stała `USOS_WEB_BASE_URL` w `Config.kt`).

## Funkcje

- **Logowanie przez USOS (OAuth 1.0a)** — request token → autoryzacja w
  przeglądarce → powrót do aplikacji przez schemat URI
  `edziennik://oauth-callback` → access token zapisany trwale (DataStore).
  Zadanie synchronizacji w tle włącza się po zalogowaniu, wyłącza po
  wylogowaniu.
- **Pulpit** — plan zajęć na najbliższe 7 dni. Na dole pasek zakładek:
  Płatności / Oceny / Plan / Wyloguj (w kolorze tła, z prostokątnym
  efektem dotknięcia — spójnie z resztą motywu).
- **Plan miesięczny** — przełącznik roku akademickiego, zakładki miesięcy
  i pasek szybkiego wyboru dnia (tylko dni z zajęciami). Wybranie dnia
  filtruje widok do tego jednego dnia; ponowne kliknięcie wraca do całego
  miesiąca. Kolorowe karty zajęć wg formy (wykład/laboratorium/...),
  klikalne do strony grupy.
- **Oceny** — pogrupowane po semestrze i przedmiocie, z punktami ECTS i
  średnią ważoną. Przy każdej ocenie kolorowa plakietka formy zajęć
  (nazwa formy dociągana osobno: `grades/terms2` daje tylko `unit_id`, więc
  `courses/units` → `classtype_id`, potem `courses/classtypes_index` →
  nazwa). Rozwinięcie oceny pokazuje datę wpisania, autora i — jeśli ocena
  ma przypisany egzamin — procentowy rozkład ocen całej grupy
  (`examrep/exam`), z własnym słupkiem w innym kolorze.
- **Płatności** (osobna zakładka) — dwie karty: **Nierozliczone** i
  **Rozliczone**. Na zakładce nierozliczonych na górze suma "Do zapłaty
  łącznie". Każda należność to zwijana karta (typ + strzałka) ze
  szczegółami: termin, kwota, numer konta i opis, plus przycisk **Kopiuj
  numer konta**. Kryterium "czy zapłacone" to pole `state` (nie
  `saldo_amount` — potwierdzone na żywych danych). Typy nierozliczonych
  przychodzą jako enum (`tuition_fee` → „Czesne"), rozliczonych jako kod
  etapu (`ETAP_CZESNE`); oba sprowadzane do wspólnego słownika, a
  nierozpoznane/puste to „Odsetki" (wg opisu) lub „Inne".
- **Strona grupy** (klik w kartę zajęć) — przedmiot, forma, numer grupy,
  semestr, prowadzący (klikalni) i pełna lista uczestników (sortowana
  alfabetycznie po nazwisku, wyświetlana jako „Nazwisko Imię"), łącznie
  z samym zalogowanym użytkownikiem.
- **Strona osoby** (klik w prowadzącego) — zatrudnienie, dyżur, kontakt.
  Zdjęcie z USOS albo awatar z inicjałami (Coil).
- **Powiadomienia** — sprawdzanie w tle co 12 h (`sync/SyncWorker.kt`,
  WorkManager), niezależnie od tego, czy aplikacja jest otwarta. Porównuje
  plan na 14 dni i wszystkie oceny z ostatnim znanym stanem; przy zmianie
  (nowe/zniknięte/przesunięte zajęcia, nowa ocena) wysyła jedno zbiorcze
  powiadomienie systemowe i dopisuje wpis do zakładki „Powiadomienia"
  (dzwonek na pulpicie). Historia działa nawet bez zgody na powiadomienia
  systemowe.
- **Trwały cache** (`data/DiskCache.kt`) — dane widoczne od razu przy
  wejściu na ekran, nawet po restarcie aplikacji. Przycisk odświeżania w
  pasku górnym wymusza świeże pobranie i nadpisuje cache; jeśli wymuszone
  odświeżenie zawiedzie (np. brak sieci), ekran zostaje przy ostatnio
  pokazanych danych z małym banerem błędu zamiast pustego ekranu. Cache
  nie wygasa — trwa do ręcznego odświeżenia.
- **Tryb ciemny** — automatyczny, wg ustawień systemu.
- **Wygląd „ledgerowy"** przeniesiony z wersji webowej — ta sama para
  fontów (Source Serif 4 do nagłówków, Inter do treści, dołączone w
  `res/font/`), ostre rogi 2–3 px zamiast zaokrąglonych kart Material,
  te same kolory (`ui/theme/Color.kt`), własne ikony wektorowe
  (`res/drawable/ic_*.xml`). Paski systemowe (stanu i nawigacji) w kolorze
  tła aplikacji.

## Pobranie gotowego APK

Każdy push na `main` automatycznie buduje APK i wystawia go jako release na
GitHubie (`.github/workflows/build-apk.yml`):

- **Releases** → tag `latest-build` (nadpisywany przy każdym commicie) —
  jeden stały link do najnowszego APK.
- albo **Actions** → wybrany run → sekcja *Artifacts* (przydatne, gdy
  chcesz APK z konkretnego commita).

APK nie ma wbudowanego klucza USOS i działa od razu po instalacji — pod
warunkiem, że serwer podpisujący (niżej) jest wdrożony i jego adres jest
wpisany w `Config.kt`.

## Wdrożenie serwera podpisującego (jednorazowo)

Zanim aplikacja zadziała, trzeba raz wdrożyć Worker z folderu
[`proxy/`](proxy/) na własnym (darmowym) koncie Cloudflare. Pełna
instrukcja: [`proxy/README.md`](proxy/README.md). W skrócie, z katalogu
`proxy/`:

```
npx wrangler login
npx wrangler secret put USOS_CONSUMER_KEY
npx wrangler secret put USOS_CONSUMER_SECRET
npx wrangler deploy
```

`wrangler deploy` wypisze adres Workera (np.
`https://edziennik-usos-proxy.<subdomena>.workers.dev`) — wklej go jako
`USOS_BASE_URL` w
`app/src/main/kotlin/pl/opole/edziennik/Config.kt` i wypchnij commit
(CI zbuduje APK z nowym adresem). Zmiana samego `proxy/src/index.js`
wymaga tylko ponownego `npx wrangler deploy` — adres się nie zmienia,
więc aplikacja nie wymaga wtedy przebudowania.

## Uruchomienie z Android Studio

1. Otwórz folder `native_app/` w Android Studio (Open → wybierz ten
   folder). Jeśli zabraknie Gradle Wrappera, Android Studio zaproponuje
   jego dogenerowanie.
2. Zsynchronizuj Gradle i napraw wszystko, co czerwone — wersje
   zależności w `app/build.gradle.kts` mogą wymagać drobnej korekty
   zależnie od wersji Android Studio.
3. Uruchom na emulatorze albo telefonie (Run ▶).

**Build z terminala:** ustaw `JAVA_HOME` na JDK 17 —
`JAVA_HOME=/ścieżka/do/jdk-17 ./gradlew assembleDebug`. Pod JDK 25
demon kompilatora Kotlina 1.9.24 wysypuje się na parsowaniu numeru
wersji JVM. W Android Studio: Settings → Build Tools → Gradle → *Gradle
JDK* = pobrany JDK 17 (nie wbudowany JBR, który bywa w wersji 25).

**Czyszczenie zombie-demonów:** jeśli po reboocie pojawi się błąd o
brakującym `/tmp/ijMapper*.gradle` albo stary błąd wraca mimo zmian —
`./gradlew --stop` i restart Android Studio. `/tmp` bywa `tmpfs`
(czyszczony przy reboocie), a demon Gradle trzyma nieaktualne ścieżki.

### Callback OAuth

Jeśli logowanie zwróci błąd o niedozwolonym adresie zwrotnym, dodaj
`edziennik://oauth-callback` jako dozwolony callback w ustawieniach
aplikacji na https://usosapps.po.edu.pl/developers/. W wersji webowej
dynamiczny callback działał bez rejestrowania dodatkowych adresów, więc
jest szansa, że tu też zadziała od razu.

## Struktura projektu

```
native_app/
  proxy/                     — serwer podpisujący OAuth1 (Worker Cloudflare)
    src/index.js             — cała logika: rozbiór żądania, podpis OAuth1,
                               forward do USOS
    wrangler.toml            — konfiguracja Workera
    README.md                — instrukcja wdrożenia
  app/src/main/kotlin/pl/opole/edziennik/
    Config.kt                — stałe: adres Workera, adres USOS, callback
                               OAuth (bez sekretów)
    MainActivity.kt          — punkt wejścia, obsługa callbacku i powiadomień
    oauth/                   — logowanie (request/access token), trwały token
    network/                 — klient HTTP do serwera podpisującego
    data/                    — parsowanie odpowiedzi USOS (odpowiednik
                               fetch_* z app.py), cache na dysku
    viewmodel/               — stan ekranów (StateFlow + ViewModel)
    ui/
      dashboard/ plan/ grades/ payments/ group/ person/
      notifications/ login/  — ekrany Compose
      components/             — SessionCard, PersonRow, ErrorBanner,
                                AppIconButton
      theme/                  — kolory, typografia, kształty (wygląd ledger)
    sync/                    — cykliczne sprawdzanie w tle (WorkManager)
  .github/workflows/build-apk.yml — CI: build APK + rolling release
```

## Stack

Kotlin 1.9.24 · AGP 8.5.2 · Jetpack Compose (BOM 2024.06) · Material 3 ·
Navigation Compose · DataStore Preferences · OkHttp · WorkManager · Coil ·
`minSdk 26`, `targetSdk 34`. OAuth1 (HMAC-SHA1) po stronie Workera
(Cloudflare Workers, Web Crypto API).
