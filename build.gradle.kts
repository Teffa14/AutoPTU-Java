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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val oracleTargeting = providers.gradleProperty("oracleTargeting")
val oracleMovement = providers.gradleProperty("oracleMovement")
val oracleCalculations = providers.gradleProperty("oracleCalculations")
val oraclePtuTables = providers.gradleProperty("oraclePtuTables")
val oracleTurnFlow = providers.gradleProperty("oracleTurnFlow")
val oracleJump = providers.gradleProperty("oracleJump")
val oracleAccuracy = providers.gradleProperty("oracleAccuracy")
val oracleStats = providers.gradleProperty("oracleStats")

tasks.test {
    useJUnitPlatform()
    if (oracleTargeting.isPresent) systemProperty("autoptu.targeting.oracle", oracleTargeting.get())
    if (oracleMovement.isPresent) systemProperty("autoptu.movement.oracle", oracleMovement.get())
    if (oracleCalculations.isPresent) systemProperty("autoptu.calculations.oracle", oracleCalculations.get())
    if (oraclePtuTables.isPresent) systemProperty("autoptu.ptu.tables.oracle", oraclePtuTables.get())
    if (oracleTurnFlow.isPresent) systemProperty("autoptu.turnflow.oracle", oracleTurnFlow.get())
    if (oracleJump.isPresent) systemProperty("autoptu.jump.oracle", oracleJump.get())
    if (oracleAccuracy.isPresent) systemProperty("autoptu.accuracy.oracle", oracleAccuracy.get())
    if (oracleStats.isPresent) systemProperty("autoptu.stats.oracle", oracleStats.get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
