import org.gradle.kotlin.dsl.buildConfigField

plugins {
    alias(libs.plugins.buildconfig)
}

val projectPackage = rootProject.group.toString()

dependencies {
    // 这些类型会出现在 common 的公开方法里, 平台模块编译时也需要
    api(libs.sparrow.yaml)
    api(libs.sparrow.redis.message.broker)
    // 代理平台自带 netty, 只打包 Lettuce 和平台缺少的 DNS 解析模块
    api(libs.lettuce.core) { isTransitive = false }
    implementation(libs.caffeine)
    implementation(libs.reactor.core)
    implementation(libs.proxy.netty.resolver.dns) { isTransitive = false }
    implementation(libs.proxy.netty.codec.dns) { isTransitive = false }
    compileOnly(libs.proxy.netty.handler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platformLauncher)
    testImplementation(libs.proxy.netty.handler)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

buildConfig {
    packageName = "$projectPackage.proxy.common"
    className = "BuildInfo"

    buildConfigField("VERSION", project.version.toString())
    buildConfigField("CONFIG_VERSION", libs.versions.proxy.config.version.get())
}
