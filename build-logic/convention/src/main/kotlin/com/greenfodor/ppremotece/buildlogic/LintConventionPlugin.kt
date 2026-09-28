package com.greenfodor.ppremotece.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class LintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("detekt"))
            pluginManager.apply(libs.pluginId("ktlint"))

            extensions.configure<DetektExtension> {
                toolVersion.set(libs.version("detekt"))
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))
                buildUponDefaultConfig.set(true)
            }

            extensions.configure<KtlintExtension> {
                additionalEditorconfig.set(
                    mapOf(
                        "ktlint_standard_chain-method-continuation" to "disabled",
                        "ktlint_standard_function-signature" to "disabled",
                        "ktlint_standard_function-naming" to "disabled",
                        "ktlint_standard_package-name" to "disabled",
                        "ktlint_standard_filename" to "disabled",
                        "ktlint_standard_multiline-expression-wrapping" to "disabled"
                    )
                )
            }

            dependencies {
                add("detektPlugins", libs.library("detekt-compose-rules"))
            }
        }
    }
}
