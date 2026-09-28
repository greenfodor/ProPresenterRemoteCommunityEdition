package com.greenfodor.ppremotece.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            pluginManager.apply(libs.pluginId("ppremotece-lint"))

            configureKotlinToolchain()
            configureJUnit()

            val apiRelease = libs.version("jvmLibraryApi")
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(apiRelease))
                    freeCompilerArgs.add("-Xjdk-release=$apiRelease")
                }
            }
            tasks.withType<JavaCompile>().configureEach {
                options.release.set(apiRelease.toInt())
            }
        }
    }
}
