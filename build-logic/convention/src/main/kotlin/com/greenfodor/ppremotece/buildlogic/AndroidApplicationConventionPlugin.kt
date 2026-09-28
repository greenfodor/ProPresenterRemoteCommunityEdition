package com.greenfodor.ppremotece.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-application"))
            pluginManager.apply(libs.pluginId("kotlin-compose"))
            pluginManager.apply(libs.pluginId("kotlin-serialization"))
            pluginManager.apply(libs.pluginId("ppremotece-lint"))

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK
                buildFeatures.compose = true
            }

            dependencies {
                add("implementation", platform(libs.library("androidx-compose-bom")))
                add("implementation", platform(libs.library("koin-bom")))
            }
        }
    }
}
