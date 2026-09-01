import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Dane USOS Consumer Key/Secret NIE trafiają do kodu ani do repozytorium —
// czytamy je z local.properties (zignorowane przez git, patrz .gitignore),
// tak jak sdk.dir. Uzupełnij je tam analogicznie do .env aplikacji webowej
// (patrz README).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "pl.opole.edziennik"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.opole.edziennik"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String", "USOS_CONSUMER_KEY",
            "\"${localProperties.getProperty("USOS_CONSUMER_KEY", "")}\"",
        )
        buildConfigField(
            "String", "USOS_CONSUMER_SECRET",
            "\"${localProperties.getProperty("USOS_CONSUMER_SECRET", "")}\"",
        )
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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
