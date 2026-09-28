package com.greenfodor.ppremotece.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private const val JVM_LIBRARY_API_RELEASE = 17

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            pluginManager.apply(libs.pluginId("ppremotece-lint"))

            configureKotlinToolchain()
            configureJUnit()

            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    freeCompilerArgs.add("-Xjdk-release=$JVM_LIBRARY_API_RELEASE")
                }
            }
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}
