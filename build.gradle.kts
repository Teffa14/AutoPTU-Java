plugins {
    java
}

group = "io.autoptu"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

val oracleTargeting = providers.gradleProperty("oracleTargeting")
val oracleMovement = providers.gradleProperty("oracleMovement")

tasks.test {
    useJUnitPlatform()
    if (oracleTargeting.isPresent) {
        systemProperty("autoptu.targeting.oracle", oracleTargeting.get())
    }
    if (oracleMovement.isPresent) {
        systemProperty("autoptu.movement.oracle", oracleMovement.get())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
