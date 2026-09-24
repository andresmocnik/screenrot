plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Deliberately a plain Kotlin/JVM module, not android-library. The damage math has zero
// Android dependencies, so it builds and unit-tests in plain `gradle :core:test` in a fraction
// of the time an Android module needs, and it's trivially reusable from a future iOS core
// (via Kotlin Multiplatform) without dragging Android APIs along.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
