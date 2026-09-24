import buildlogic.InitializeRunDirectory
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("xyz.jpenilla.run-paper")
}

val runTemplatesDirectory = rootProject.layout.projectDirectory.dir("buildSrc/run-templates")
val javaToolchains = extensions.getByType<JavaToolchainService>()
val java25 = javaToolchains.launcherFor {
    vendor = JvmVendorSpec.JETBRAINS
    languageVersion = JavaLanguageVersion.of(25)
}
val projectJar = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val bukkitPluginJars = rootProject.fileTree("buildSrc/bukkit-plugin") {
    include("*.jar")
}

// 本地提供服务端 JAR 后才注册 Spigot 任务.
val spigotJar = rootProject.layout.projectDirectory.file("buildSrc/server-jars/spigot-26.2.jar")
if (spigotJar.asFile.isFile) {
    val spigotDirectory = rootProject.layout.projectDirectory.dir("run/spigot/26.2")
    val prepareSpigot = tasks.register<InitializeRunDirectory>("prepareSpigot_26.2") {
        templateDirectories.from(runTemplatesDirectory.dir("backend/spigot"))
        targetDirectory.set(spigotDirectory)
    }
    tasks.register<RunServer>("runSpigot_26.2") {
        group = "run paper"
        displayName.set("Spigot 26.2")
        description = "Run the standalone Spigot 26.2 server on port 25568."
        minecraftVersion("26.2")
        runDirectory.set(spigotDirectory)
        legacyPluginLoading()
        pluginJars.from(projectJar, bukkitPluginJars)
        serverJar(spigotJar.asFile)
        javaLauncher.set(java25)
        minHeapSize = "1536M"
        maxHeapSize = "1536M"

        systemProperties["Paper.IgnoreJavaVersion"] = true
        systemProperties["${rootProject.group}.dev"] = true
        systemProperties["com.mojang.eula.agree"] = true
        jvmArgs(
            "-Dorg.bukkit.plugin.java.LibraryLoader.centralURL=https://maven.aliyun.com/repository/central",
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
            "-Ddisable.watchdog=true",
            "-Xlog:redefine+class*=info",
            "-XX:+AllowEnhancedClassRedefinition"
        )
        dependsOn(prepareSpigot)
    }
}
