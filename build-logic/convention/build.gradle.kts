plugins {
    `kotlin-dsl`
}

group = "com.greenfodor.ppremotece.buildlogic"

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.ktlint.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.ppremotece.android.application.get().pluginId
            implementationClass = "com.greenfodor.ppremotece.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.ppremotece.android.library.asProvider().get().pluginId
            implementationClass = "com.greenfodor.ppremotece.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = libs.plugins.ppremotece.android.library.compose.get().pluginId
            implementationClass = "com.greenfodor.ppremotece.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.ppremotece.jvm.library.get().pluginId
            implementationClass = "com.greenfodor.ppremotece.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.ppremotece.android.feature.get().pluginId
            implementationClass = "com.greenfodor.ppremotece.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("lint") {
            id = libs.plugins.ppremotece.lint.get().pluginId
            implementationClass = "com.greenfodor.ppremotece.buildlogic.LintConventionPlugin"
        }
    }
}
