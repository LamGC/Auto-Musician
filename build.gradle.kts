import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    application
}

group = "net.lamgc"
version = "1.0.0-rc3"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    // Logging
    implementation("io.github.microutils:kotlin-logging:2.0.11")
    implementation("ch.qos.logback:logback-classic:1.2.7")

    implementation("org.apache.httpcomponents.client5:httpclient5:5.1.1")

    // Json
    implementation("com.squareup.moshi:moshi:1.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.12.0")

    implementation("org.ktorm:ktorm-core:3.4.1")
    runtimeOnly("mysql:mysql-connector-java:8.0.27")

    implementation("com.cronutils:cron-utils:9.1.6")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.5.31")

    // Ktor
    val ktorVersion = "1.6.4"
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("net.lamgc.automusician.ServerMainKt")
    val workdirPath = System.getenv("PROJECT_RUN_WORKDIR") ?: "./build/run"
    val workdir = File(workdirPath)
    if (!workdir.exists()) {
        workdir.mkdirs()
    }
    tasks["run"].setProperty("workingDir", workdir)

}