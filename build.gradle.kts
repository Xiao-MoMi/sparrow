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
    // proxy 只是代理端模块的目录, 本身不产出 jar
    if (path == ":proxy") return@subprojects

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

        shadowJar relocation@{
            val libs = "$projectPackage.libraries"
            // 代理端直接使用平台自带的 Adventure 和 netty, 只重定位自己打包进去的库
            if (project.path.startsWith(":proxy:")) {
                val libs = "$projectPackage.proxy.libraries"
                relocate("net.momirealms.sparrow.yaml", "$libs.yaml")
                relocate("net.momirealms.sparrow.redis.messagebroker", "$libs.redis.messagebroker")
                relocate("com.github.benmanes.caffeine", "$libs.caffeine")
                relocate("com.google.errorprone", "$libs.errorprone")
                relocate("org.jspecify", "$libs.jspecify")
                relocate("io.lettuce", "$libs.lettuce")
                relocate("reactor", "$libs.reactor")
                relocate("org.reactivestreams", "$libs.reactivestreams")
                relocate("io.netty.handler.codec.dns", "$libs.netty.handler.codec.dns")
                relocate("io.netty.resolver.dns", "$libs.netty.resolver.dns")
                return@relocation
            }

            // Relocate
            relocate("net.kyori", libs)
            relocate("net.momirealms.sparrow.reflection", "$libs.reflection")
            relocate("net.momirealms.sparrow.yaml", "$libs.yaml")
            relocate("net.momirealms.sparrow.ui", "$libs.ui")
            relocate("net.momirealms.sparrow.redis.messagebroker", "$libs.redis.messagebroker")
            relocate("net.momirealms.sparrow.message", "$libs.message")
            relocate("net.momirealms.sparrow.expr", "$libs.expr")
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
    shadowJar {
        // 合并各平台已经完成重定位的产物, 保留各自的依赖命名空间.
        val platformJars = listOf(":core", ":proxy:velocity", ":proxy:bungeecord")
        for (platform in platformJars) {
            dependsOn("$platform:shadowJar")
            from(provider { zipTree(project(platform).tasks.named<Jar>("shadowJar").get().archiveFile.get()) })
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        mergeServiceFiles()
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        archiveFileName = "${rootProject.name}-${project.version}.jar"
        destinationDirectory.set(layout.projectDirectory.dir("target"))
    }
    assemble {
        dependsOn(shadowJar)
    }
    clean {
        delete("$rootDir/target")
    }
}
