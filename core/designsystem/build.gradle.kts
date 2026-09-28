plugins {
    alias(libs.plugins.ppremotece.android.library.compose)
}

android {
    namespace = "com.greenfodor.ppremotece.core.designsystem"
}

dependencies {
    implementation(project(":core:domain"))
}
