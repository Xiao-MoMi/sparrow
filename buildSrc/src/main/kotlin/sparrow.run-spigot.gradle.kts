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

// 为本地提供的每个 Spigot 服务端 JAR 注册运行任务.
val spigotJars = rootProject.fileTree("buildSrc/server-jars") {
    include("spigot-*.jar")
}
for (spigotJar in spigotJars.sortedBy { it.name }) {
    val minecraftVersion = spigotJar.name.removePrefix("spigot-").removeSuffix(".jar")
    val spigotDirectory = rootProject.layout.projectDirectory.dir("run/spigot/$minecraftVersion")
    val prepareSpigot = tasks.register<InitializeRunDirectory>("prepareSpigot_$minecraftVersion") {
        templateDirectories.from(runTemplatesDirectory.dir("backend/spigot"))
        targetDirectory.set(spigotDirectory)
    }
    tasks.register<RunServer>("runSpigot_$minecraftVersion") {
        group = "run paper"
        displayName.set("Spigot $minecraftVersion")
        description = "Run the standalone Spigot $minecraftVersion server on port 25568."
        minecraftVersion(minecraftVersion)
        runDirectory.set(spigotDirectory)
        legacyPluginLoading()
        pluginJars.from(projectJar, bukkitPluginJars)
        serverJar(spigotJar)
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
