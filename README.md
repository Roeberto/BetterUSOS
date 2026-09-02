# e-dziennik — natywna aplikacja Android (Kotlin + Jetpack Compose)

Natywny odpowiednik aplikacji webowej z `../app.py`, rozmawiający bezpośrednio
z USOS API (bez pośrednictwa serwera Flask) — czyli działa też offline od
Twojego komputera, o ile masz zasięg internetu na telefonie.

## Ważne zastrzeżenie

Projekt buduje się czysto przez `./gradlew assembleDebug` (zweryfikowane
wielokrotnie) i był testowany na żywym urządzeniu przez Ciebie w trakcie
kolejnych iteracji — logowanie, cache, sync w tle i większość ekranów
działały poprawnie. Traktuj to mimo wszystko jako projekt rozwijany
iteracyjnie, nie w pełni "gotowy produkt" — kolejne funkcje wciąż mogą
ujawnić przypadki brzegowe, których nie przewidzieliśmy.

## Co jest zaimplementowane

- **Ekran konfiguracji klucza USOS** (`SetupScreen`/`CredentialsStore`) —
  appka nie ma wbudowanego na stałe klucza/sekretu konsumenta USOS (ani w
  kodzie, ani w publikowanym APK); użytkownik wpisuje go raz, przy
  pierwszym uruchomieniu, zapisywany trwale (DataStore). Dzięki temu
  zbudowany APK (patrz "Pobranie gotowego APK" niżej) można bezpiecznie
  publikować, nawet w publicznym repo. Lokalne buildy mogą pominąć ten
  ekran, jeśli `local.properties` ma wypełniony klucz — patrz
  "Uruchomienie z Android Studio".
- **Logowanie przez USOS (OAuth 1.0a)** — request token → autoryzacja w
  przeglądarce → powrót do aplikacji przez niestandardowy schemat URI
  (`edziennik://oauth-callback`) → access token zapisany trwale (DataStore).
- **Pulpit** — plan zajęć na najbliższe 7 dni; pasek na dole z zakładkami
  Płatności/Oceny/Plan/Wyloguj.
- **Płatności** (osobna zakładka) — lista należności z tą samą logiką
  filtrowania po `state`, którą ustaliliśmy na żywych danych (`saldo_amount`
  nie oznacza "do zapłaty" — patrz `fetchOutstandingPayments()`).
- **Plan miesięczny** — przełącznik roku akademickiego + zakładki miesięcy,
  te same kolorowe karty zajęć co w wersji webowej, klikalne do strony grupy.
- **Strona ocen** (`/oceny`) — oceny pogrupowane po semestrze i przedmiocie,
  punkty ECTS, średnia ważona, forma zajęć przy każdej ocenie (wykład/
  ćwiczenia/projekt/...) — `grades/terms2` daje tylko opaque `unit_id`, więc
  nazwa formy jest dociągana osobno przez `courses/units` (unit_id →
  classtype_id) + `courses/classtypes_index` (słownik ID → nazwa, metoda
  publiczna, znaleziona przez `apiref`, ten sam trik co przy rozkładzie ocen).
  Rozwinięcie oceny pokazuje datę wpisania, autora i — jeśli ocena ma
  przypisany egzamin — rozkład procentowy ocen całej grupy (`examrep/exam`),
  z własnym słupkiem pokolorowanym inaczej. Bezpośrednie przeniesienie już
  zweryfikowanego na żywych danych parsowania `grades/terms2` z app.py
  (patrz `data/Grades.kt`).
- **Strona grupy** (klik w kartę zajęć na pulpicie/planie) — przedmiot,
  forma zajęć, prowadzący (klikalni) i pozostali uczestnicy.
- **Strona osoby** (klik w prowadzącego) — zatrudnienie, dyżur, kontakt;
  zdjęcie z USOS albo awatar z inicjałami (Coil do ładowania zdjęć).
- **Tryb ciemny** — automatyczny, wg ustawień systemu (Material3 daje to
  za darmo, bez ręcznego przełącznika jak w wersji webowej).
- **Spójny wygląd "ledgerowy"** przeniesiony z wersji webowej — ta sama
  para fontów (Source Serif 4 do nagłówków, Inter do treści, dołączone na
  stałe w `res/font/`), ostre 2-3px rogi zamiast domyślnych zaokrąglonych
  kart Material, dokładnie te same kolory (`ui/theme/Color.kt`). Prawdziwe
  ikony wektorowe (`res/drawable/ic_*.xml`) zamiast emoji jako zamienników
  ikon.
- **Trwały cache odpowiedzi USOS API** (`data/DiskCache.kt`) — dane widoczne
  od razu przy wejściu na ekran (bez czekania na sieć), nawet po restarcie
  appki. Przycisk odświeżania w pasku górnym każdego ekranu wymusza świeże pobranie
  i nadpisuje cache; jeśli wymuszone odświeżenie akurat zawiedzie (np. brak
  sieci), ekran zostaje przy ostatnio pokazanych danych zamiast pustego
  ekranu z błędem. Cache nie ma czasu wygasania — trwa do ręcznego
  odświeżenia.
- **Sprawdzanie w tle co 12h** (`sync/SyncWorker.kt`, WorkManager) — działa
  niezależnie od tego, czy appka jest otwarta. Porównuje plan na najbliższe
  14 dni i wszystkie oceny z ostatnim znanym stanem; jeśli coś się zmieniło
  (nowe/zniknięte/przesunięte zajęcia, nowa ocena), wysyła jedno zbiorcze
  powiadomienie systemowe i zapisuje wpis w nowej zakładce "Powiadomienia"
  (dzwonek na pulpicie) — ta historia zostaje nawet bez zgody na
  powiadomienia systemowe (Android 13+ pyta o nią przy pierwszym uruchomieniu).
  Zadanie włącza się po zalogowaniu i wyłącza po wylogowaniu.

## Pobranie gotowego APK

Każdy push na `main` automatycznie buduje APK i wystawia go jako release na
GitHubie — pobierz najnowszy z zakładki
[Releases](../../releases/tag/latest-build) (tag `latest-build`, nadpisywany
przy każdym commicie), albo z zakładki
[Actions](../../actions/workflows/build-apk.yml) → wybrany run → Artifacts.

Appka **nie ma wbudowanego na stałe** klucza USOS — dzięki temu ten APK
jest bezpieczny do publikacji nawet w publicznym repo, mimo że każdy APK
da się zdekompilować. Po instalacji appka poprosi o podanie Consumer
Key/Secret przy pierwszym uruchomieniu (patrz "Uruchomienie z Android
Studio" niżej po sposób na pominięcie tego ekranu przy własnych, lokalnych
buildach).

## Uruchomienie z Android Studio

1. Otwórz folder `native_app/` w Android Studio (Open → wybierz ten
   folder). Android Studio powinno samo zaproponować dogenerowanie
   Gradle Wrappera, jeśli go zabraknie.
2. Zsynchronizuj Gradle (Android Studio zrobi to samo po otwarciu) i
   napraw wszystko, co czerwone — wersje zależności w `app/build.gradle.kts`
   mogą wymagać drobnej korekty w zależności od wersji Android Studio.
3. Uruchom na emulatorze albo prawdziwym telefonie (Run ▶).
4. Opcjonalnie: dodaj `USOS_CONSUMER_KEY`/`USOS_CONSUMER_SECRET` do
   `local.properties` (plik zignorowany przez git) — appka zbudowana w ten
   sposób skonfiguruje się sama, pomijając ekran ustawień. Bez tego przy
   pierwszym uruchomieniu appka poprosi o wpisanie tych danych ręcznie —
   te same co w `.env` aplikacji webowej (albo zarejestruj własną aplikację
   na usosapps.po.edu.pl/developers). Zapisywane trwale na telefonie
   (`CredentialsStore`, DataStore) — nie trzeba wpisywać przy
   każdym uruchomieniu, tylko raz.

### Callback OAuth — jedna rzecz do sprawdzenia

Jeśli logowanie zwróci błąd o niedozwolonym adresie zwrotnym, może być
potrzebne dodanie `edziennik://oauth-callback` jako dozwolonego callbacku
w ustawieniach Twojej aplikacji na
https://usosapps.po.edu.pl/developers/ — dokładnie ten sam problem, który
w wersji webowej rozwiązaliśmy dynamicznym wyliczaniem adresu po stronie
serwera; tu, bez serwera pośredniczącego, trzeba będzie ustawić to raz,
na sztywno (o ile ta instalacja USOS w ogóle to sprawdza — w wersji
webowej dynamiczny callback działał bez rejestrowania dodatkowych
adresów, więc jest szansa, że zadziała od razu).

## Struktura projektu

```
native_app/
  app/src/main/kotlin/pl/opole/edziennik/
    Config.kt              — stałe (adres USOS, callback OAuth) — bez sekretów
    MainActivity.kt         — punkt wejścia, bramka ekranu konfiguracji
    oauth/                  — podpisywanie OAuth1, logowanie, trwały token,
                               CredentialsStore (klucz USOS wpisany przez usera)
    network/                — klient HTTP do USOS API
    data/                   — parsowanie odpowiedzi USOS (odpowiednik app.py)
    viewmodel/               — stan ekranów
    ui/                      — ekrany Compose (setup/login/dashboard/plan/
                               oceny/płatności/grupa/osoba/powiadomienia)
                               + motyw
    sync/                    — cykliczne sprawdzanie w tle (WorkManager)
```
