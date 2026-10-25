plugins {
    kotlin("jvm") version "2.2.10"
    application
}

group = "si.jacen.aura"
version = "0.1.0"

dependencies {
    implementation("io.ktor:ktor-server-core:3.6.0")
    implementation("io.ktor:ktor-server-cio:3.6.0")
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

application {
    mainClass.set("si.jacen.aura.backend.MainKt")
    applicationDefaultJvmArgs = listOf("-Xms64m", "-Xmx256m")
}

kotlin {
    compilerOptions {
        // 服务器为 OpenJDK 25，本地编译产物以 21 字节码发布，两端均可运行
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}
