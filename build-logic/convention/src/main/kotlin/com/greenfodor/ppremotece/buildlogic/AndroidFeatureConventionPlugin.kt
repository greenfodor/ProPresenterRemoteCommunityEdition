package com.greenfodor.ppremotece.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("ppremotece-android-library-compose"))
            pluginManager.apply(libs.pluginId("kotlin-serialization"))

            dependencies {
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))

                add("implementation", platform(libs.library("koin-bom")))
                add("implementation", libs.library("koin-androidx-compose"))
                add("implementation", libs.library("koin-compose-navigation3"))

                add("implementation", libs.library("androidx-navigation3-runtime"))
                add("implementation", libs.library("androidx-navigation3-ui"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-navigation3"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))

                add("testImplementation", libs.library("assertk"))
                add("testImplementation", libs.library("turbine"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
