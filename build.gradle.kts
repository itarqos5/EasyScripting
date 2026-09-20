plugins { java }

group = "dev.easyscripting"
version = "0.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://maven.maxhenkel.de/repository/public")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paperVersion").getOrElse("1.21.8-R0.1-SNAPSHOT")}")
    compileOnly("net.citizensnpcs:citizens-main:2.0.43-SNAPSHOT") { isTransitive = false }
    compileOnly("net.citizensnpcs:citizensapi:2.0.43-SNAPSHOT") { isTransitive = false }
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.0")
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}
// Newer Paper APIs are published for Java 25; bytecode stays on the shared Java 21 surface.
configurations.compileClasspath {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.test { useJUnitPlatform() }
tasks.jar { archiveBaseName.set("EasyScripting") }

// Explicitly opt-in integration fixture. Never included in the distributable plugin.
val smoke by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}
tasks.register<Jar>("smokeJar") {
    group = "verification"
    description = "Builds a local-server integration test plugin (not for production)."
    archiveBaseName.set("EasyScripting-SmokeTests")
    from(smoke.output)
}
