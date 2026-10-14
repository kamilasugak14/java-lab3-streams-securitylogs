plugins {
    java
    jacoco
}

group = "ua.kpi.comsys"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        // java.util.stream.Gatherer є стабільним API починаючи з Java 24; використовуємо LTS 25
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
