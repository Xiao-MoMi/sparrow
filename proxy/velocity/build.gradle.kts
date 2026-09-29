plugins {
    id("sparrow.run-velocity")
}

val projectName = rootProject.name

dependencies {
    implementation(project(":proxy:common"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}

// velocity-api 4.x 以 Java 25 编译, 需要 Java 25 编译器读取, 产物仍输出 Java 21 字节码
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    disableAutoTargetJvm()
}

tasks {
    withType<JavaCompile> {
        options.release = 21
    }

    shadowJar {
        archiveFileName = "$projectName-velocity-${project.version}.jar"
        destinationDirectory.set(file("$rootDir/target"))
    }
}
