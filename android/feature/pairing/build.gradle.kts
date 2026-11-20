plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "fr.penpenic.babyphone.feature.pairing"
}

dependencies {
    testImplementation(libs.junit)
}
