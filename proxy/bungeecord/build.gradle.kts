plugins {
    id("sparrow.run-waterfall")
    alias(libs.plugins.bungee.plugin.yml)
}

val projectName = rootProject.name
val projectPackage = rootProject.group.toString()

dependencies {
    implementation(project(":proxy:common"))
    compileOnly(libs.bungeecord.api)
}

tasks {
    shadowJar {
        archiveFileName = "$projectName-bungeecord-${project.version}.jar"
    }
}

// bungee.yml
bungee {
    name = projectName
    main = "$projectPackage.proxy.bungeecord.BungeeCordSparrow"
    author = "XiaoMoMi"
}
