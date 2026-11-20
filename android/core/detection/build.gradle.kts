plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "fr.penpenic.babyphone.core.detection"
}

dependencies {
    testImplementation(libs.junit)
}
