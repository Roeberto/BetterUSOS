// Cloudflare Worker — przezroczysty "podpisujący reverse proxy" dla
// usosapps.po.edu.pl/services/*.
//
// Appka Android NIE zna klucza/sekretu konsumenta USOS w ogóle — woła ten
// Worker zamiast USOS bezpośrednio, dorzucając tylko oauth_token/
// oauth_token_secret zalogowanego użytkownika (albo request-tokena, w
// trakcie logowania). Ten Worker dolicza podpis OAuth 1.0a (dokładnie ten
// sam algorytm, co usunięty po stronie appki `OAuth1Signer.kt`) i
// przekazuje zapytanie dalej do USOS, zwracając odpowiedź bez zmian.
//
// USOS_CONSUMER_KEY/USOS_CONSUMER_SECRET to sekrety Workera
// (`wrangler secret put ...`) — nigdy nie ma ich w tym pliku ani w gicie.

const USOS_BASE_URL = "https://usosapps.po.edu.pl";

/** RFC3986-ścisłe procentowe kodowanie wymagane przez OAuth 1.0a.
 * `encodeURIComponent` domyślnie zostawia nietknięte `! ' ( ) *`, których
 * OAuth wymaga zakodować — dokładny odpowiednik fixupów w `OAuth1Signer.kt`
 * (`URLEncoder` + ręczne poprawki `*`/`~`). `~` JS zostawia poprawnie samo. */
function percentEncode(value) {
  return encodeURIComponent(value).replace(
    /[!'()*]/g,
    (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase(),
  );
}

function randomNonce() {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function arrayBufferToBase64(buffer) {
  let binary = "";
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

async function hmacSha1Base64(key, message) {
  const enc = new TextEncoder();
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    enc.encode(key),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", cryptoKey, enc.encode(message));
  return arrayBufferToBase64(signature);
}

/** Dokładny port `OAuth1Signer.sign()` — ta sama kolejność kroków:
 * zbuduj oauth_* pola, posortuj WSZYSTKIE parametry (surowe klucze, tak
 * jak Kotlinowy `toSortedMap()`), zbuduj base string, podpisz kluczem
 * `consumerSecret&tokenSecret` (oba procentowo zakodowane). */
async function signOAuth1(method, url, params, consumerKey, consumerSecret, token, tokenSecret) {
  const oauthParams = {
    oauth_consumer_key: consumerKey,
    oauth_nonce: randomNonce(),
    oauth_signature_method: "HMAC-SHA1",
    oauth_timestamp: String(Math.floor(Date.now() / 1000)),
    oauth_version: "1.0",
  };
  if (token) oauthParams.oauth_token = token;

  const allParams = { ...params, ...oauthParams };
  const sortedKeys = Object.keys(allParams).sort();
  const paramString = sortedKeys
    .map((k) => `${percentEncode(k)}=${percentEncode(allParams[k])}`)
    .join("&");
  const baseString = [method.toUpperCase(), percentEncode(url), percentEncode(paramString)].join("&");
  const signingKey = `${percentEncode(consumerSecret)}&${percentEncode(tokenSecret || "")}`;

  oauthParams.oauth_signature = await hmacSha1Base64(signingKey, baseString);
  return oauthParams;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const match = url.pathname.match(/^\/services\/(.+)$/);
    if (!match) {
      return new Response("Not found — oczekiwana ścieżka /services/<metoda USOS>", { status: 404 });
    }
    const method = match[1];
    const httpMethod = request.method.toUpperCase();
    if (httpMethod !== "GET" && httpMethod !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    const params = {};
    if (httpMethod === "GET") {
      for (const [k, v] of url.searchParams.entries()) params[k] = v;
    } else {
      const contentType = request.headers.get("content-type") || "";
      if (contentType.includes("application/x-www-form-urlencoded")) {
        const body = await request.text();
        for (const [k, v] of new URLSearchParams(body).entries()) params[k] = v;
      }
    }

    // oauth_token/oauth_token_secret to para tokena zalogowanego
    // użytkownika (albo request tokena w trakcie logowania). token_secret
    // służy TYLKO do wyliczenia podpisu — prawdziwe OAuth1 nigdy go nie
    // wysyła dalej do serwera, więc usuwamy go z parametrów przed forwardem.
    const token = params.oauth_token;
    const tokenSecret = params.oauth_token_secret;
    delete params.oauth_token;
    delete params.oauth_token_secret;

    const targetUrl = `${USOS_BASE_URL}/services/${method}`;
    const oauthParams = await signOAuth1(
      httpMethod,
      targetUrl,
      params,
      env.USOS_CONSUMER_KEY,
      env.USOS_CONSUMER_SECRET,
      token,
      tokenSecret,
    );
    const allParams = { ...params, ...oauthParams };

    let upstreamResponse;
    if (httpMethod === "GET") {
      const qs = new URLSearchParams(allParams).toString();
      upstreamResponse = await fetch(`${targetUrl}?${qs}`, { method: "GET" });
    } else {
      const body = new URLSearchParams(allParams).toString();
      upstreamResponse = await fetch(targetUrl, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body,
      });
    }

    const responseBody = await upstreamResponse.text();
    return new Response(responseBody, {
      status: upstreamResponse.status,
      headers: { "content-type": upstreamResponse.headers.get("content-type") || "application/json" },
    });
  },
};
