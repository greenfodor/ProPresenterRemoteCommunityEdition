import java.util.Properties

plugins {
    alias(libs.plugins.ppremotece.android.application)
}

/** The properties in [file]. */
fun propertiesOf(file: File): Properties = Properties().apply { file.inputStream().use(::load) }

/**
 * The file `release.signing.properties` in `local.properties` names (relative to the project
 * root), or null when `local.properties` or that entry is missing; a named file that does not
 * exist fails the build.
 */
val releaseSigningFile: File? =
    rootProject.file("local.properties").takeIf { it.isFile }
        ?.let(::propertiesOf)
        ?.getProperty("release.signing.properties")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { path ->
            rootProject.file(path).also {
                if (!it.isFile) throw GradleException("release.signing.properties names a missing file: $it")
            }
        }

/** The trimmed value of [key] in the release signing file; a missing or blank value fails the build. */
fun Properties.signingValue(key: String): String =
    getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("$releaseSigningFile has no value for $key")

android {
    namespace = "com.greenfodor.ppremotece"

    defaultConfig {
        applicationId = "com.greenfodor.ppremotece"
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        releaseSigningFile?.let { signingFile ->
            val signing = propertiesOf(signingFile)
            create("release") {
                storeFile = signingFile.parentFile.resolve(signing.signingValue("storeFile"))
                storePassword = signing.signingValue("storePassword")
                keyAlias = signing.signingValue("keyAlias")
                keyPassword = signing.signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(project(":feature:audio"))
    implementation(project(":feature:looks"))
    implementation(project(":feature:props"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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
