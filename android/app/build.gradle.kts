plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Version unique du projet, issue du tag git `vX.Y.Z` (voir docs/RELEASING.md).
 * La CI de release la passe dans BABYPHONE_VERSION ; en local on obtient "0.0.0-dev".
 * versionCode = X × 1 000 000 + Y × 1 000 + Z : toujours croissant, ce qu'exige Android pour une mise à jour.
 */
val appVersion: String = providers.environmentVariable("BABYPHONE_VERSION").orElse("0.0.0-dev").get().removePrefix("v")
val appVersionCode: Int = run {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?$""").matchEntire(appVersion)
        ?: error("Version invalide « $appVersion » : format attendu X.Y.Z ou X.Y.Z-suffixe")
    val (major, minor, patch) = match.destructured.toList().take(3).map(String::toInt)
    require(minor < 1000 && patch < 1000) { "minor et patch doivent rester sous 1000" }
    maxOf(1, major * 1_000_000 + minor * 1_000 + patch)
}

android {
    namespace = "fr.penpenic.babyphone"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.penpenic.babyphone"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        // WebRTC embarque une bibliothèque native par architecture (6 à 13 Mo chacune) :
        // la release ne garde que les téléphones (ARM), le debug ajoute l'émulateur 64 bits.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // La signature release lit sa configuration dans l'environnement (secrets CI), jamais dans le dépôt.
    val releaseKeystore = System.getenv("BABYPHONE_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("BABYPHONE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BABYPHONE_KEY_ALIAS")
                keyPassword = System.getenv("BABYPHONE_KEY_PASSWORD")
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            ndk { abiFilters += "x86_64" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core:crypto"))
    implementation(project(":core:detection"))
    implementation(project(":core:media"))
    implementation(project(":core:network"))
    implementation(project(":feature:pairing"))
    implementation(project(":feature:baby"))
    implementation(project(":feature:parent"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
