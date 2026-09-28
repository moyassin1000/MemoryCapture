buildscript {
    dependencies {
        // AGP 9 uses built-in Kotlin. Pin a newer compatible KGP for Compose compiler parity.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
