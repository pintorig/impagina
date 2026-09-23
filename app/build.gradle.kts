import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Firma opzionale: le credenziali arrivano da -P in CI, mai dal repository.
// Senza di esse la release esce non firmata, che per provarla via adb basta.
val keystoreFile = findProperty("KEYSTORE_FILE") as String?

android {
    namespace = "io.github.pintorig.impagina"
    compileSdk = 37

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

/**
 * Copia l'APK di debug sotto un nome che si ordina da solo.
 *
 * `./gradlew apkDiProva` produce
 * `app/build/prova/impagina-1.9.0-20260923-1432-5e96d09-debug.apk`.
 *
 * Il nome porta versione, data e revisione perche' `versionName` da solo non
 * basta: fra un rilascio e l'altro si costruiscono decine di APK di prova, e
 * si chiamerebbero tutti uguale. Con questo ordine alfabetico e cronologico
 * coincidono, e ogni file resta riconducibile al commit da cui e' uscito.
 */
val revisioneGit: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short=7", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifBlank { "senza-git" } }

tasks.register<Copy>("apkDiProva") {
    group = "distribution"
    description = "APK di debug con versione, data e revisione nel nome."
    dependsOn("assembleDebug")

    val versione = android.defaultConfig.versionName ?: "0"
    val quando = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    val revisione = revisioneGit.get()

    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.buildDirectory.dir("prova"))
    rename { "impagina-$versione-$quando-$revisione-debug.apk" }

    doLast {
        val prodotto = layout.buildDirectory.dir("prova").get().asFile
            .listFiles()?.maxByOrNull { it.lastModified() }
        logger.lifecycle("APK di prova: ${prodotto?.absolutePath}")
    }
}
