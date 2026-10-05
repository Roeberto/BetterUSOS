plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// `versionCode` musi realnie rosnąć z buildem na build, żeby appka (patrz
// update/UpdateChecker.kt) mogła w ogóle stwierdzić "czy wersja na GitHubie
// jest nowsza niż zainstalowana". CI przekazuje numer builda GitHub Actions
// przez `-PappVersionCode=...` (rośnie z każdym pushem); lokalne buildy bez
// tej flagi dostają stałe 1 — wystarczające do kompilacji, update-checker po
// prostu nigdy nie zgłosi aktualizacji na buildzie lokalnym.
val ciVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "pl.opole.edziennik"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.opole.edziennik"
        minSdk = 26
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = "0.1.0"
    }

    // Stabilny klucz debug (dołączony w repo, keystore/debug.keystore) —
    // domyślny `~/.android/debug.keystore` jest generowany od nowa, z innym
    // losowym kluczem, na każdej świeżej maszynie CI. Bez tego każdy build z
    // GitHub Actions miał inny podpis, więc instalacja nowszego APK nad
    // starszym kończyła się błędem "Nie zainstalowano" (konflikt podpisów),
    // zamiast zaktualizować appkę. Ten klucz nie chroni niczego tajnego —
    // jego jedyna rola to spójność między buildami, dlatego bezpiecznie
    // trzymać go w publicznym repo (tak samo jak w wersji webowej nigdy nie
    // trzymamy żadnego PRAWDZIWEGO sekretu w kodzie — patrz proxy/).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // TopAppBar (i inne Material3 API, których używamy) jest oznaczone
        // jako eksperymentalne — bez tej zgody Kotlin traktuje samo jego
        // użycie jak błąd kompilacji, nie ostrzeżenie.
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }

    buildFeatures {
        compose = true
        // Daje BuildConfig.VERSION_CODE/VERSION_NAME (standardowe pola AGP,
        // żadnych własnych sekretów — te zniknęły razem z lokalnym podpisywaniem
        // OAuth1, patrz proxy/) — potrzebne update-checkerowi do porównania z
        // wersją na GitHubie.
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Ładowanie zdjęć osób z USOS API (grupa/osoba) — awatar z inicjałami
    // jako fallback, gdy USOS nie ma zdjęcia (patrz `formatPerson()`).
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Cykliczne sprawdzanie planu/ocen w tle, niezależnie od tego, czy
    // appka jest otwarta (patrz sync/SyncWorker.kt).
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
