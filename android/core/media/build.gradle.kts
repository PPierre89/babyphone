plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "fr.penpenic.babyphone.core.media"
}

dependencies {
    api(libs.webrtc)
    api(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
