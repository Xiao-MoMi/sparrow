import org.gradle.kotlin.dsl.buildConfigField
import net.minecrell.pluginyml.paper.PaperPluginDescription
import java.text.SimpleDateFormat
import java.util.Date

val projectName = rootProject.name
val projectPackage = rootProject.group.toString()
val projectId = projectPackage.substringAfterLast('.')
val proxyJarName = "$projectName-proxy.jarinjar"

// Plugin
plugins {
    id("sparrow.run-servers")
    id("sparrow.run-spigot")
    id("sparrow.run-velocity")
    alias(libs.plugins.plugin.yml)
    alias(libs.plugins.bukkit.plugin.yml)
    alias(libs.plugins.buildconfig)
}

// Dependency
dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api)

    compileOnly(project(":bukkit-proxy"))
    implementation(project(":common-files"))

    compileOnly(libs.mojang.brigadier)
    compileOnly(libs.cloud.core)
    compileOnly(libs.cloud.paper)
    compileOnly(libs.cloud.minecraft.extras)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.caffeine)

    compileOnly(libs.bundles.adventure)
    implementation(libs.bundles.sparrow.nbt)
    implementation(libs.sparrow.yaml)
    implementation(libs.sparrow.ui)
    implementation(libs.sparrow.redis.message.broker)
    implementation(libs.concurrentutil) { isTransitive = false }

    compileOnly(libs.sparrow.reflection)
    compileOnly(libs.datafixerupper)
    compileOnly(libs.lettuce.core)
    compileOnly(libs.mongodb.driver.sync)
    compileOnly(libs.jdbi.core)
    compileOnly(libs.hikari.cp)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platformLauncher)
    testImplementation(libs.cloud.core)
    testImplementation(libs.cloud.bukkit)
    testRuntimeOnly(libs.cloud.minecraft.extras)
    testImplementation(libs.mockito.core)
    testImplementation(project(":bukkit-proxy"))
    testImplementation(libs.sparrow.reflection)
    testImplementation(libs.lettuce.core)
    testImplementation(libs.caffeine)
    testRuntimeOnly(libs.mysql.connector.j)
    testRuntimeOnly(libs.mariadb.driver)
    testRuntimeOnly(libs.postgresql.driver)
    testImplementation(libs.test.paper.api)
    testImplementation(libs.mongodb.driver.sync)
    testImplementation(libs.jdbi.core)
    testImplementation(libs.hikari.cp)
}

// Version
buildConfig {
    packageName = "$projectPackage.plugin.dependency"
    className = "DependencyVersions"

    buildConfigField("MYSQL_SCHEMA_VERSION", libs.versions.mysql.schema.version.get().toInt())
    buildConfigField("POSTGRESQL_SCHEMA_VERSION", libs.versions.postgresql.schema.version.get().toInt())
    buildConfigField("MONGODB_INDEX_VERSION", libs.versions.mongodb.index.version.get().toInt())

    buildConfigField("PROJECT_PACKAGE", projectPackage)
    buildConfigField("PROJECT_ID", projectId)
    buildConfigField("PROXY_JAR_NAME", proxyJarName)
    buildConfigField("ASM_CLASS_PREFIX", projectPackage.replace('.', '_'))
    buildConfigField("COMPILE_TIME", SimpleDateFormat("yyyyMMdd_HHmm").format(Date()))
    buildConfigField("CONFIG_VERSION", libs.versions.config.version.get())
    buildConfigField("COMMANDS_CONFIG_VERSION", libs.versions.commands.config.version.get())
    buildConfigField("FEATURES_CONFIG_VERSION", libs.versions.features.config.version.get())
    buildConfigField("SERVER_CONFIG_VERSION", libs.versions.server.config.version.get())
    buildConfigField("LANG_VERSION", libs.versions.lang.version.get())
    // ASM
    buildConfigField("ASM", libs.versions.asm.get())
    buildConfigField("ASM_COMMONS", libs.versions.asmcommons.get())
    buildConfigField("JAR_RELOCATOR", libs.versions.jar.relocator.get())
    // COMMON
    buildConfigField("CAFFEINE", libs.versions.caffeine.get())
    buildConfigField("MONGODB_DRIVER", libs.versions.mongodb.driver.get())
    buildConfigField("JDBI", libs.versions.jdbi.get())
    buildConfigField("HIKARI_CP", libs.versions.hikari.cp.get())
    buildConfigField("MYSQL_DRIVER", libs.versions.mysql.driver.get())
    buildConfigField("MARIADB_DRIVER", libs.versions.mariadb.driver.get())
    buildConfigField("POSTGRESQL_DRIVER", libs.versions.postgresql.driver.get())
    buildConfigField("CHECKER_QUAL", libs.versions.checker.qual.get())
    buildConfigField("REACTIVE_STREAMS", libs.versions.reactive.streams.get())
    // LETTUCE
    buildConfigField("LETTUCE", libs.versions.lettuce.get())
    buildConfigField("JACKSON", libs.versions.jackson.core.get())
    buildConfigField("JACKSON_ANNOTATIONS", libs.versions.jackson.annotations.get())
    buildConfigField("JACKSON_DATATYPE", libs.versions.jackson.datatype.get())
    buildConfigField("NETTY", libs.versions.netty.get())
    buildConfigField("REACTOR", libs.versions.reactor.get())
    // CLOUD
    buildConfigField("GEANTYREF", libs.versions.geantyref.get())
    buildConfigField("CLOUD_CORE", libs.versions.cloud.core.get())
    buildConfigField("CLOUD_BRIGADIER", libs.versions.cloud.brigadier.get())
    buildConfigField("CLOUD_SERVICES", libs.versions.cloud.services.get())
    buildConfigField("CLOUD_BUKKIT", libs.versions.cloud.bukkit.get())
    buildConfigField("CLOUD_PAPER", libs.versions.cloud.paper.get())
    buildConfigField("CLOUD_MINECRAFT_EXTRAS", libs.versions.cloud.minecraft.extras.get())
    // ADVENTURE
    buildConfigField("ADVENTURE", libs.versions.adventure.get())
    buildConfigField("OPTION", libs.versions.option.get())
    buildConfigField("EXAMINATION_API", libs.versions.examination.api.get())
}

// Tasks
tasks {
    shadowJar {
        mergeServiceFiles()
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        from(project(":bukkit-proxy").tasks.shadowJar.flatMap { it.archiveFile })
        archiveFileName = "$projectName-${project.version}.jar"
        destinationDirectory.set(file("$rootDir/target"))
    }

    test {
        useJUnitPlatform()
    }
}

// plugin.yml
bukkit {
    name = projectName
    main = "$projectPackage.plugin.SpigotJavaPlugin"
    apiVersion = "1.21.11"
    authors = listOf("XiaoMoMi")
    contributors = listOf("g2213swo", "jhqwqmc")
    softDepend = listOf("PlaceholderAPI", "LuckPerms")
    foliaSupported = true
}

// paper-plugin.yml
paper {
    name = projectName
    bootstrapper = "$projectPackage.plugin.PaperBootstrap"
    main = "$projectPackage.plugin.PaperJavaPlugin"
    apiVersion = "1.21.11"
    authors = listOf("XiaoMoMi")
    contributors = listOf("g2213swo", "jhqwqmc")
    foliaSupported = true
    serverDependencies {
        register("PlaceholderAPI") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
            joinClasspath = true
        }
        register("LuckPerms") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
            joinClasspath = true
        }
    }
}
