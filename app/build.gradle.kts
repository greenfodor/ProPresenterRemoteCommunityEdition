import java.util.Properties

plugins {
    alias(libs.plugins.ppremotece.android.application)
}

/** The properties in [file], or null when it does not exist. */
fun propertiesOf(file: File): Properties? =
    file.takeIf { it.isFile }?.let { Properties().apply { it.inputStream().use(::load) } }

/** The release signing properties named by `release.signing.properties` in `local.properties`, if any. */
val releaseSigning: Properties? =
    propertiesOf(rootProject.file("local.properties"))
        ?.getProperty("release.signing.properties")
        ?.let { propertiesOf(File(it)) }

android {
    namespace = "com.greenfodor.ppremotece"

    defaultConfig {
        applicationId = "com.greenfodor.ppremotece"
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:connect"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:remote"))
    implementation(project(":feature:clear"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:timers"))
    implementation(project(":feature:macros"))
    implementation(project(":feature:looks"))
    implementation(project(":feature:props"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation3)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.navigation3)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil)

    testImplementation(libs.assertk)
}
