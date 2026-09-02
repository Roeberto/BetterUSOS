# Serwer podpisujący USOS OAuth1 (Cloudflare Worker)

Jeden plik JS (`src/index.js`), który trzyma prawdziwy klucz/sekret
konsumenta USOS **po stronie serwera** — appka Android nigdy go nie zna,
więc publiczny APK nie ujawnia go nawet po zdekompilowaniu. Appka woła ten
Worker zamiast `usosapps.po.edu.pl` bezpośrednio; Worker dolicza podpis
OAuth 1.0a (ten sam algorytm, co usunięty z appki `OAuth1Signer.kt`) i
przekazuje zapytanie dalej do USOS, zwracając odpowiedź bez zmian.

`oauth_token`/`oauth_token_secret` (token zalogowanego użytkownika albo
request token w trakcie logowania) appka wysyła jako zwykłe parametry
zapytania — Worker używa `oauth_token_secret` wyłącznie do wyliczenia
podpisu i nigdy nie przekazuje go dalej do USOS.

## Wdrożenie (jednorazowo, na własnym koncie Cloudflare)

1. Zainstaluj Wrangler (CLI Cloudflare) — nie trzeba globalnie, wystarczy
   `npx`:
   ```
   npx wrangler --version
   ```
2. Zaloguj się (otworzy przeglądarkę, potrzebne darmowe konto Cloudflare):
   ```
   npx wrangler login
   ```
3. W tym folderze (`proxy/`) ustaw sekrety — Wrangler poprosi o wpisanie
   wartości interaktywnie, nie trafiają do żadnego pliku ani do gita:
   ```
   npx wrangler secret put USOS_CONSUMER_KEY
   npx wrangler secret put USOS_CONSUMER_SECRET
   ```
   (te same wartości, co dotąd w `.env` aplikacji webowej / na
   usosapps.po.edu.pl/developers).
4. Wdróż:
   ```
   npx wrangler deploy
   ```
   Na końcu Wrangler wypisze adres, pod którym Worker działa, np.
   `https://edziennik-usos-proxy.<twoja-subdomena>.workers.dev`.
5. Wklej ten adres jako `USOS_BASE_URL` w
   `app/src/main/kotlin/pl/opole/edziennik/Config.kt` i zbuduj appkę
   ponownie (albo po prostu wypchnij commit — CI zbuduje APK z nowym
   adresem).

## Aktualizacja

Każda zmiana `src/index.js` wymaga ponownego `npx wrangler deploy` — sam
adres Workera się nie zmienia, więc appka nie wymaga wtedy żadnej zmiany.

## Dlaczego to bezpieczne

`USOS_CONSUMER_KEY`/`USOS_CONSUMER_SECRET` istnieją wyłącznie jako sekrety
Workera (`wrangler secret put`) — zaszyfrowane u Cloudflare, nigdy w
kodzie, nigdy w gicie, nigdy w żadnym pliku na dysku. Appka Android (ani
publiczny APK z CI, ani lokalny build) nie ma i nigdy nie miała dostępu do
tych wartości — komunikuje się wyłącznie z tym Workerem, podając co
najwyżej token zalogowanego użytkownika (oauth_token/oauth_token_secret),
nie klucz konsumenta.
