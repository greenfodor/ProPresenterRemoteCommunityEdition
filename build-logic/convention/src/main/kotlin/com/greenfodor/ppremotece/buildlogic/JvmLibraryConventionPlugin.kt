package com.greenfodor.ppremotece.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

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

            pluginManager.apply(libs.pluginId("animalsniffer"))
            val androidSignature = libs.library("gummy-bears-api29").get()
            dependencies {
                add("signature", "${androidSignature.module}:${androidSignature.versionConstraint.requiredVersion}@signature")
            }
            extensions.configure<AnimalSnifferExtension> {
                sourceSets = listOf(extensions.getByType<SourceSetContainer>().getByName("main"))
            }
        }
    }
}
