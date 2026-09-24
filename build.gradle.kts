import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java")
    `java-library`
    alias(libs.plugins.shadow)
}

val projectPackage = "net.momirealms.sparrow"
group = projectPackage
version = libs.versions.project.version.get()

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

val versionCatalog = libs
subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "com.gradleup.shadow")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
        withSourcesJar()
    }

    repositories {
        mavenCentral()
        maven("https://libraries.minecraft.net/")
        maven("https://repo.catnies.top/releases/")
        maven("https://repo.momirealms.net/releases/")
        maven("https://repo.momirealms.net/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
    }

    dependencies {
        compileOnly(versionCatalog.jetbrains.annotations)
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_21.toString()
        targetCompatibility = JavaVersion.VERSION_21.toString()
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-XDignore.symbol.file",
        ))
        dependsOn(tasks.clean)
    }

    tasks.named("assemble") {
        dependsOn("shadowJar")
    }

    tasks {
        build {
            dependsOn(shadowJar)
        }

        shadowJar {
            // Relocate
            val libs = "$projectPackage.libraries"
            relocate("net.kyori", libs)
            relocate("net.momirealms.sparrow.yaml", "$libs.yaml")
            relocate("net.momirealms.sparrow.ui", "$libs.ui")
            relocate("net.momirealms.antigrieflib", "$libs.antigrieflib")
            relocate("net.momirealms.sparrow.nbt", "$libs.nbt")
            relocate("cn.gtemc.levelerbridge", "$libs.levelerbridge")
            relocate("org.incendo", libs)
            relocate("dev.dejvokep", libs)
            relocate("com.github.benmanes.caffeine", "$libs.caffeine")
            relocate("org.snakeyaml", "$libs.snakeyaml")
            relocate("org.ahocorasick", "$libs.ahocorasick")
            relocate("net.jpountz", "$libs.jpountz")
            relocate("software.amazon.awssdk", "$libs.awssdk")
            relocate("software.amazon.eventstream", "$libs.eventstream")
            relocate("com.google.common.jimfs", "$libs.jimfs")
            relocate("org.apache.commons", "$libs.commons")
            relocate("io.leangen.geantyref", "$libs.geantyref")
            relocate("org.jdbi", "$libs.jdbi")
            relocate("com.zaxxer.hikari", "$libs.hikari")
            relocate("com.mysql", "$libs.mysql")
            relocate("org.mariadb.jdbc", "$libs.mariadb")
            relocate("org.postgresql", "$libs.postgresql")
            relocate("ca.spottedleaf.concurrentutil", "$libs.concurrentutil")
            relocate("io.netty.handler.codec.http", "$libs.netty.handler.codec.http")
            relocate("io.netty.handler.codec.rtsp", "$libs.netty.handler.codec.rtsp")
            relocate("io.netty.handler.codec.spdy", "$libs.netty.handler.codec.spdy")
            relocate("io.netty.handler.codec.http2", "$libs.netty.handler.codec.http2")
            relocate("io.netty.handler.codec.dns", "$libs.netty.handler.codec.dns")
            relocate("io.netty.resolver.dns", "$libs.netty.resolver.dns")
            relocate("io.github.bucket4j", "$libs.bucket4j")
        }
    }
}

tasks {
    clean {
        delete("$rootDir/target")
    }
}
