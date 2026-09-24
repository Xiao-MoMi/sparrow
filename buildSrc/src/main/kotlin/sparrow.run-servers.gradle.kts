import buildlogic.InitializeRunDirectory
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
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

// 给运行的不同版本的 Paper/Folia 映射 paper-global.yml 的配置版本.
val paperConfigurationVersions = mapOf(
    "1.21.8" to "30",
    "1.21.10" to "31",
    "1.21.11" to "31",
    "26.1.2" to "31",
    "26.2" to "31"
)



/**
 * 配置和注册后端服务器测试.
 */
tasks.withType<RunServer>().configureEach {
    if (name == "runServer" || name == "runDevBundleServer") {
        group = null
        enabled = false
    }
}

val minecraftVersions = listOf("1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2")
val projectJar = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val extraPluginJars = rootProject.fileTree("buildSrc/plugin") {
    include("*.jar")
}
fun RunServer.configureServer(
    display: String,
    minecraftVersion: String,
    directory: String,
    maximumHeap: String? = null
) {
    // 启动前复制插件, 避免服务端直接持有共享的 shadowJar.
    legacyPluginLoading()
    group = "run paper"
    displayName.set(display)
    minecraftVersion(minecraftVersion)
    runDirectory.set(rootProject.layout.projectDirectory.dir(directory))
    pluginJars.from(projectJar)
    javaLauncher.set(javaLauncherFor(minecraftVersion))

    if (maximumHeap != null) {
        minHeapSize = maximumHeap
        maxHeapSize = maximumHeap
    }

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

for (minecraftVersion in minecraftVersions) {
    // 代理后端使用独立目录.
    val paperProxyDirectory = rootProject.layout.projectDirectory.dir("run/proxy/paper/$minecraftVersion")
    val foliaProxyDirectory = rootProject.layout.projectDirectory.dir("run/proxy/folia/$minecraftVersion")

    val paperConfigurationVersion = paperConfigurationVersions.getValue(minecraftVersion)
    val commonBackendTemplates = runTemplatesDirectory.dir("backend/common")
    val versionBackendTemplates = runTemplatesDirectory.dir("backend/versions/$paperConfigurationVersion")

    val prepareProxyPaper = tasks.register<InitializeRunDirectory>("prepareProxyPaper_$minecraftVersion") {
        templateDirectories.from(
            commonBackendTemplates,
            versionBackendTemplates,
            runTemplatesDirectory.dir("backend/paper")
        )
        targetDirectory.set(paperProxyDirectory)
    }

    val prepareProxyFolia = tasks.register<InitializeRunDirectory>("prepareProxyFolia_$minecraftVersion") {
        templateDirectories.from(
            commonBackendTemplates,
            versionBackendTemplates,
            runTemplatesDirectory.dir("backend/folia")
        )
        targetDirectory.set(foliaProxyDirectory)
    }

    tasks.register<RunServer>("runProxyPaper_$minecraftVersion") {
        configureServer(
            "Proxy Paper $minecraftVersion",
            minecraftVersion,
            "run/proxy/paper/$minecraftVersion",
            "1536M"
        )
        description = "Run the Paper $minecraftVersion proxy backend on port 25566."
        pluginJars.from(extraPluginJars)
        dependsOn(prepareProxyPaper)
    }

    tasks.register<RunServer>("runProxyFolia_$minecraftVersion") {
        configureServer(
            "Proxy Folia $minecraftVersion",
            minecraftVersion,
            "run/proxy/folia/$minecraftVersion",
            "1536M"
        )
        description = "Run the Folia $minecraftVersion proxy backend on port 25567."
        pluginJars.from(extraPluginJars)
        downloadsApiService.set(DownloadsAPIService.folia(project))
        dependsOn(prepareProxyFolia)
    }
}
