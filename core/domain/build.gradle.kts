plugins {
    alias(libs.plugins.ppremotece.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.assertk)
}
