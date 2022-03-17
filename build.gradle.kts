import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    application
}

group = "net.lamgc"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    // Logging
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    implementation("org.apache.httpcomponents.client5:httpclient5:5.1.3")

    // Json
    implementation("com.google.code.gson:gson:2.9.0")

    implementation("org.ktorm:ktorm-core:3.4.1")
    runtimeOnly("mysql:mysql-connector-java:8.0.28")

    implementation("com.cronutils:cron-utils:9.1.6")

    implementation("org.mapdb:mapdb:3.0.8")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.5.31")

    implementation("org.bouncycastle:bcprov-jdk15to18:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.70")

    // Ktor
    val ktorVersion = "1.6.7"
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    targetCompatibility = JavaVersion.VERSION_11.majorVersion
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
}

application {
    mainClass.set("net.lamgc.automusician.ServerMainKt")
    val workdirPath = System.getenv("PROJECT_RUN_WORKDIR") ?: "./build/run"
    val workdir = File(workdirPath)
    if (!workdir.exists()) {
        workdir.mkdirs()
    }
    tasks["run"].setProperty("workingDir", workdir)
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=true"
    )
}