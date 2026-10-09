plugins {
    id("java")
}

group = "com.ocloud"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val nativeGui = sourceSets.create("nativeGui") {
    java.srcDir("src/native/java")
    compileClasspath += sourceSets.main.get().output
}
val releaseTest = sourceSets.create("releaseTest") {
    java.srcDir("src/releaseTest/java")
    compileClasspath += sourceSets.test.get().compileClasspath
    runtimeClasspath += sourceSets.test.get().runtimeClasspath
}
val paperApiVersion = providers.gradleProperty("paperApiVersion").getOrElse("1.21-R0.1-SNAPSHOT")
val nativeApiVersion = providers.gradleProperty("nativeApiVersion").getOrElse("1.21.8-R0.1-SNAPSHOT")

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    add(nativeGui.compileOnlyConfigurationName, "io.papermc.paper:paper-api:$nativeApiVersion")
    runtimeOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testImplementation("org.ow2.asm:asm:9.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(nativeGui.output)
    providers.gradleProperty("runtimePaperApiVersion").orNull?.let {
        testRuntimeOnly("io.papermc.paper:paper-api:$it")
    }
}

configurations.testRuntimeClasspath {
    attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(providers.gradleProperty("javaRelease").map { it.toInt() }.getOrElse(21))
}

java {
    sourceCompatibility = JavaVersion.toVersion(providers.gradleProperty("javaRelease").getOrElse("21"))
    targetCompatibility = sourceCompatibility
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    manifest { attributes["paperweight-mappings-namespace"] = "mojang" }
    dependsOn(nativeGui.classesTaskName)
    from(nativeGui.output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    dependsOn(nativeGui.classesTaskName)
    useJUnitPlatform()
}

val releaseJar = tasks.register<Copy>("releaseJar") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into(layout.buildDirectory.dir("release"))
}

val verifyRelease = tasks.register<Test>("verifyRelease") {
    dependsOn(releaseJar, releaseTest.classesTaskName)
    testClassesDirs = releaseTest.output.classesDirs
    classpath = releaseTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("release.jar", layout.buildDirectory.file("release/oCloudTaiXiu-${project.version}.jar").get().asFile.absolutePath)
}

tasks.register<Zip>("sharePlugin") {
    group = "distribution"
    description = "Build, verify and package the plugin with its documentation and sample configuration."
    dependsOn(tasks.test, verifyRelease)
    destinationDirectory.set(layout.buildDirectory.dir("release"))
    archiveFileName.set("oCloudTaiXiu-${project.version}-share.zip")
    from(tasks.jar)
    from("distribution/README.txt")
    from("distribution/DOCS.md")
    from("src/main/resources") { include("taixiu.yml", "gui.yml", "bedrock/taixiu.yml"); into("config") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
