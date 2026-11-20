plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "fr.penpenic.babyphone.core.crypto"
}

dependencies {
    testImplementation(libs.junit)
}
