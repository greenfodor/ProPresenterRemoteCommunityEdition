plugins {
    alias(libs.plugins.ppremotece.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.greenfodor.ppremotece.core.data"
}

dependencies {
    implementation(project(":core:domain"))
}
