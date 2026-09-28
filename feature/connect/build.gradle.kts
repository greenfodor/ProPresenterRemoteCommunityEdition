plugins {
    alias(libs.plugins.ppremotece.android.feature)
}

android {
    namespace = "com.greenfodor.ppremotece.feature.connect"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
