package com.greenfodor.ppremotece.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("ppremotece-android-library"))
            pluginManager.apply(libs.pluginId("kotlin-compose"))

            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true
            }

            dependencies {
                val composeBom = platform(libs.library("androidx-compose-bom"))
                add("implementation", composeBom)
                add("implementation", libs.library("androidx-compose-ui"))
                add("implementation", libs.library("androidx-compose-material3"))
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
            }
        }
    }
}
