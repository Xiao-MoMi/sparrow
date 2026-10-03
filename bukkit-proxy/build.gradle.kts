dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.datafixerupper)
    implementation(libs.sparrow.reflection)
}

val projectName = rootProject.name

tasks {
    shadowJar {
        archiveClassifier = ""
        archiveFileName = "$projectName-proxy.jarinjar"
    }
}
