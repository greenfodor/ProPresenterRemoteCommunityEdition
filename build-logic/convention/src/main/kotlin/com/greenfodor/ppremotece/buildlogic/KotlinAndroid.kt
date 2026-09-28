package com.greenfodor.ppremotece.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 37
internal const val MIN_SDK = 29
internal const val JAVA_VERSION = 25

internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version = release(COMPILE_SDK)
        }
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_25
        compileOptions.targetCompatibility = JavaVersion.VERSION_25
        testOptions.unitTests.all { it.useJUnitPlatform() }
    }
    configureKotlinToolchain()
    configureJUnit()
}

internal fun Project.configureKotlinToolchain() {
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(JAVA_VERSION)
    }
}

internal fun Project.configureJUnit() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    dependencies {
        add("testImplementation", libs.library("junit-jupiter"))
        add("testRuntimeOnly", libs.library("junit-platform-launcher"))
    }
}
