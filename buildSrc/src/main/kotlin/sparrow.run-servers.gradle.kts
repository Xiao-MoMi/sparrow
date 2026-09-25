import buildlogic.InitializeRunDirectory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService

plugins {
    id("io.papermc.paperweight.userdev")
    id("xyz.jpenilla.run-paper")
}

/**
 * 配置模板和运行时环境.
 */
val runTemplatesDirectory = rootProject.layout.projectDirectory.dir("buildSrc/run-templates")
val javaToolchains = extensions.getByType<JavaToolchainService>()
val java21 = javaToolchains.launcherFor {
    vendor = JvmVendorSpec.JETBRAINS
    languageVersion = JavaLanguageVersion.of(21)
}
val java25 = javaToolchains.launcherFor {
    vendor = JvmVendorSpec.JETBRAINS
    languageVersion = JavaLanguageVersion.of(25)
}

// 按版本挑 JDK
fun javaLauncherFor(minecraftVersion: String): Provider<JavaLauncher> =
    if (minecraftVersion.startsWith("26.")) java25 else java21

/**
 * 配置和注册后端服务器测试.
 */

// 版本表
val paperVersions = listOf("1.21.11", "26.1.2", "26.2", "26.3")
val foliaVersions = listOf("1.21.11", "26.1.2", "26.2")

// 插件自带的那两个任务没有对应的运行目录, 关掉免得误启动
tasks.withType<RunServer>().configureEach {
    if (name == "runServer" || name == "runDevBundleServer") {
        group = null
        enabled = false
    }
}

val projectJar = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val extraPluginJars = rootProject.fileTree("buildSrc/plugin") {
    include("*.jar")
}
val commonBackendTemplates = runTemplatesDirectory.dir("backend/common")
val versionBackendTemplates = runTemplatesDirectory.dir("backend/versions/31")

// 服务端公共配置
fun RunServer.configureServer(display: String, minecraftVersion: String, directory: String) {
    // 启动前复制插件, 避免服务端直接持有共享的 shadowJar.
    legacyPluginLoading()
    group = "run paper"
    displayName.set(display)
    minecraftVersion(minecraftVersion)
    runDirectory.set(rootProject.layout.projectDirectory.dir(directory))
    pluginJars.from(projectJar, extraPluginJars)
    javaLauncher.set(javaLauncherFor(minecraftVersion))

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
}

// 任务注册
for (minecraftVersion in paperVersions) {
    val directory = "run/paper/$minecraftVersion"
    val prepare = tasks.register<InitializeRunDirectory>("preparePaper_$minecraftVersion") {
        templateDirectories.from(
            commonBackendTemplates,
            versionBackendTemplates,
            runTemplatesDirectory.dir("backend/paper")
        )
        targetDirectory.set(rootProject.layout.projectDirectory.dir(directory))
    }
    tasks.register<RunServer>("runPaper_$minecraftVersion") {
        description = "Run a Paper $minecraftVersion server with Sparrow on port 25566."
        configureServer("Paper $minecraftVersion", minecraftVersion, directory)
        dependsOn(prepare)
    }
}

for (minecraftVersion in foliaVersions) {
    val directory = "run/folia/$minecraftVersion"
    val prepare = tasks.register<InitializeRunDirectory>("prepareFolia_$minecraftVersion") {
        templateDirectories.from(
            commonBackendTemplates,
            versionBackendTemplates,
            runTemplatesDirectory.dir("backend/folia")
        )
        targetDirectory.set(rootProject.layout.projectDirectory.dir(directory))
    }
    tasks.register<RunServer>("runFolia_$minecraftVersion") {
        description = "Run a Folia $minecraftVersion server with Sparrow on port 25567."
        configureServer("Folia $minecraftVersion", minecraftVersion, directory)
        downloadsApiService.set(DownloadsAPIService.folia(project))
        dependsOn(prepare)
    }
}
