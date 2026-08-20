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
val oracleActionSpace = providers.gradleProperty("oracleActionSpace")
val oracleDamage = providers.gradleProperty("oracleDamage")
val oracleMoveEvents = providers.gradleProperty("oracleMoveEvents")
val oracleShiftApplication = providers.gradleProperty("oracleShiftApplication")
val oracleMoveCombatProfile = providers.gradleProperty("oracleMoveCombatProfile")
val oracleEvasion = providers.gradleProperty("oracleEvasion")
val oracleStab = providers.gradleProperty("oracleStab")
val oracleBurnDamage = providers.gradleProperty("oracleBurnDamage")
val oracleStatusSkip = providers.gradleProperty("oracleStatusSkip")
val oracleStatusSkipExceptions = providers.gradleProperty("oracleStatusSkipExceptions")
val oracleMoveFrequency = providers.gradleProperty("oracleMoveFrequency")
val oracleRoundLifecycle = providers.gradleProperty("oracleRoundLifecycle")
val oraclePhaseLifecycle = providers.gradleProperty("oraclePhaseLifecycle")
val oraclePinkPearl = providers.gradleProperty("oraclePinkPearl")
val oracleMegaLauncher = providers.gradleProperty("oracleMegaLauncher")
val oracleTemporaryEffectPayload = providers.gradleProperty("oracleTemporaryEffectPayload")
val oracleDelayedHit = providers.gradleProperty("oracleDelayedHit")
val oracleDamageHistory = providers.gradleProperty("oracleDamageHistory")
val oracleStatusApplication = providers.gradleProperty("oracleStatusApplication")
val oracleLancerPhase = providers.gradleProperty("oracleLancerPhase")
val oraclePerkPhase = providers.gradleProperty("oraclePerkPhase")
val oracleCombatStageHooks = providers.gradleProperty("oracleCombatStageHooks")
val oracleSpatialDamageAuras = providers.gradleProperty("oracleSpatialDamageAuras")
val oracleMoveKeyword = providers.gradleProperty("oracleMoveKeyword")
val oracleAnalytic = providers.gradleProperty("oracleAnalytic")
val oracleInitiativeTurn = providers.gradleProperty("oracleInitiativeTurn")
val oraclePokemonInitiativeEntry = providers.gradleProperty("oraclePokemonInitiativeEntry")
val oracleInitiativeRoundModifier = providers.gradleProperty("oracleInitiativeRoundModifier")
val oracleInitiativeSpeedAbility = providers.gradleProperty("oracleInitiativeSpeedAbility")
val oracleInitiativeAdditionalBonus = providers.gradleProperty("oracleInitiativeAdditionalBonus")
val oracleTrainerInitiativeSpeed = providers.gradleProperty("oracleTrainerInitiativeSpeed")
val oracleTrainerInitiativeEntry = providers.gradleProperty("oracleTrainerInitiativeEntry")

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
    if (oracleActionSpace.isPresent) systemProperty("autoptu.actionspace.oracle", oracleActionSpace.get())
    if (oracleDamage.isPresent) systemProperty("autoptu.damage.oracle", oracleDamage.get())
    if (oracleMoveEvents.isPresent) systemProperty("autoptu.move.events.oracle", oracleMoveEvents.get())
    if (oracleShiftApplication.isPresent) systemProperty("autoptu.shift.application.oracle", oracleShiftApplication.get())
    if (oracleMoveCombatProfile.isPresent) systemProperty("autoptu.move.combat.profile.oracle", oracleMoveCombatProfile.get())
    if (oracleEvasion.isPresent) systemProperty("autoptu.evasion.oracle", oracleEvasion.get())
    if (oracleStab.isPresent) systemProperty("autoptu.stab.oracle", oracleStab.get())
    if (oracleBurnDamage.isPresent) systemProperty("autoptu.burn.damage.oracle", oracleBurnDamage.get())
    if (oracleStatusSkip.isPresent) systemProperty("autoptu.status.skip.oracle", oracleStatusSkip.get())
    if (oracleStatusSkipExceptions.isPresent) systemProperty("autoptu.status.skip.exception.oracle", oracleStatusSkipExceptions.get())
    if (oracleMoveFrequency.isPresent) systemProperty("autoptu.move.frequency.oracle", oracleMoveFrequency.get())
    if (oracleRoundLifecycle.isPresent) systemProperty("autoptu.round.lifecycle.oracle", oracleRoundLifecycle.get())
    if (oraclePhaseLifecycle.isPresent) systemProperty("autoptu.phase.lifecycle.oracle", oraclePhaseLifecycle.get())
    if (oraclePinkPearl.isPresent) systemProperty("autoptu.pink.pearl.oracle", oraclePinkPearl.get())
    if (oracleMegaLauncher.isPresent) systemProperty("autoptu.mega.launcher.oracle", oracleMegaLauncher.get())
    if (oracleTemporaryEffectPayload.isPresent) systemProperty("autoptu.temporary.effect.payload.oracle", oracleTemporaryEffectPayload.get())
    if (oracleDelayedHit.isPresent) systemProperty("autoptu.delayed.hit.oracle", oracleDelayedHit.get())
    if (oracleDamageHistory.isPresent) systemProperty("autoptu.damage.history.oracle", oracleDamageHistory.get())
    if (oracleStatusApplication.isPresent) systemProperty("autoptu.status.application.oracle", oracleStatusApplication.get())
    if (oracleLancerPhase.isPresent) systemProperty("autoptu.lancer.phase.oracle", oracleLancerPhase.get())
    if (oraclePerkPhase.isPresent) systemProperty("autoptu.perk.phase.oracle", oraclePerkPhase.get())
    if (oracleCombatStageHooks.isPresent) systemProperty("autoptu.combat.stage.hooks.oracle", oracleCombatStageHooks.get())
    if (oracleSpatialDamageAuras.isPresent) systemProperty("autoptu.spatial.damage.auras.oracle", oracleSpatialDamageAuras.get())
    if (oracleMoveKeyword.isPresent) systemProperty("autoptu.move.keyword.oracle", oracleMoveKeyword.get())
    if (oracleAnalytic.isPresent) systemProperty("autoptu.analytic.oracle", oracleAnalytic.get())
    if (oracleInitiativeTurn.isPresent) systemProperty("autoptu.initiative.turn.oracle", oracleInitiativeTurn.get())
    if (oraclePokemonInitiativeEntry.isPresent) systemProperty("autoptu.pokemon.initiative.entry.oracle", oraclePokemonInitiativeEntry.get())
    if (oracleInitiativeRoundModifier.isPresent) systemProperty("autoptu.initiative.round.modifier.oracle", oracleInitiativeRoundModifier.get())
    if (oracleInitiativeSpeedAbility.isPresent) systemProperty("autoptu.initiative.speed.ability.oracle", oracleInitiativeSpeedAbility.get())
    if (oracleInitiativeAdditionalBonus.isPresent) systemProperty("autoptu.initiative.additional.bonus.oracle", oracleInitiativeAdditionalBonus.get())
    if (oracleTrainerInitiativeSpeed.isPresent) systemProperty("autoptu.trainer.initiative.speed.oracle", oracleTrainerInitiativeSpeed.get())
    if (oracleTrainerInitiativeEntry.isPresent) systemProperty("autoptu.trainer.initiative.entry.oracle", oracleTrainerInitiativeEntry.get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
