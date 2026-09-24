plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("xyz.jpenilla.run-paper:xyz.jpenilla.run-paper.gradle.plugin:${libs.versions.run.task.get()}")
    implementation("io.papermc.paperweight.userdev:io.papermc.paperweight.userdev.gradle.plugin:${libs.versions.paperweight.get()}")
}
