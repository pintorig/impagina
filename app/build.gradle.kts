plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma opzionale: le credenziali arrivano da -P in CI, mai dal repository.
// Senza di esse la release esce non firmata, che per provarla via adb basta.
val keystoreFile = findProperty("KEYSTORE_FILE") as String?

android {
    namespace = "io.github.pintorig.impagina"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.pintorig.impagina"
        minSdk = 28          // ImageDecoder con gestione EXIF automatica
        targetSdk = 35
        versionCode = 10
        versionName = "1.9.0"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = findProperty("KEYSTORE_PASSWORD") as String?
                keyAlias = findProperty("KEY_ALIAS") as String?
                keyPassword = findProperty("KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Gli errori bloccano, gli avvisi no: una CI che fallisce per un
        // avviso stilistico smette di essere presa sul serio.
        abortOnError = true
        warningsAsErrors = false
        // In CI l'analisi gira già su debug: rifarla in release raddoppia
        // il tempo senza aggiungere segnale.
        checkReleaseBuilds = false
        htmlReport = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.mlkit.document.scanner)

    testImplementation(libs.junit)
}
