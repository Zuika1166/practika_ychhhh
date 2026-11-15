import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    jacoco
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.4"
}

group = "ru.practice"

version = "1.0.0"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        javaParameters = true
    }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        googleJavaFormat("1.24.0")
        target("src/**/*.java")
    }
    kotlin {
        ktfmt("0.54").kotlinlangStyle()
        target("src/**/*.kt")
    }
    kotlinGradle { ktfmt("0.54").kotlinlangStyle() }
}

jacoco { toolVersion = "0.8.13" }

tasks.test {
    useJUnitPlatform()
    systemProperty("api.version", "1.44")
    testLogging { events("passed", "skipped", "failed") }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.bootJar { archiveFileName = "helpdesk.jar" }
