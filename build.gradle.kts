// Root build: plugins declared, not applied here.
// AGP 9.0+ incluye Kotlin: NO se aplica org.jetbrains.kotlin.android (ruling en ledger).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
}
