dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.datafixerupper)
    implementation(libs.sparrow.reflection)
}

val projectName = rootProject.name
val projectPackage = rootProject.group.toString()

tasks {
    shadowJar {
        archiveClassifier = ""
        archiveFileName = "$projectName-proxy.jarinjar"
        relocate("net.momirealms.sparrow.reflection", "$projectPackage.libraries.reflection")
    }
}
