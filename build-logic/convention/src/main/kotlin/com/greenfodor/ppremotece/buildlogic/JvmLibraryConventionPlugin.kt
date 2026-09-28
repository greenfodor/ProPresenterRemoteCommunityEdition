package com.greenfodor.ppremotece.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            pluginManager.apply(libs.pluginId("ppremotece-lint"))

            configureKotlinToolchain()
            configureJUnit()
        }
    }
}
