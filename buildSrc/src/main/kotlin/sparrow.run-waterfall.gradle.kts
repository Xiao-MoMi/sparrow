import buildlogic.InitializeRunDirectory
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import xyz.jpenilla.runwaterfall.task.RunWaterfall

val waterfallDirectory = rootProject.layout.projectDirectory.dir("run/waterfall")
val runTemplatesDirectory = rootProject.layout.projectDirectory.dir("buildSrc/run-templates")
val waterfallVersion = "1.21"
val javaToolchains = extensions.getByType<JavaToolchainService>()
val java21 = javaToolchains.launcherFor {
    vendor = JvmVendorSpec.JETBRAINS
    languageVersion = JavaLanguageVersion.of(21)
}

/**
 * 配置和注册 Waterfall 运行测试.
 */
val projectJar = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
val prepareProxyWaterfall = tasks.register<InitializeRunDirectory>("prepareProxyWaterfall") {
    templateDirectories.from(runTemplatesDirectory.dir("waterfall"))
    targetDirectory.set(waterfallDirectory)
}
tasks.register<RunWaterfall>("runProxyWaterfall") {
    group = "run paper"
    description = "Run the shared Waterfall proxy on port 25565."
    displayName.set("Waterfall $waterfallVersion")

    waterfallVersion(waterfallVersion)
    runDirectory.set(waterfallDirectory)
    pluginJars.from(projectJar)
    pluginJars.from(rootProject.fileTree("buildSrc/waterfall-plugin") {
        include("*.jar")
    })
    javaLauncher.set(java21)
    minHeapSize = "512M"
    maxHeapSize = "512M"

    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8"
    )
    dependsOn(prepareProxyWaterfall)
}
