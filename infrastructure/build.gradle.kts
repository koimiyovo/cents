plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.androidx.room3)
}

android {
    namespace = "com.kyovo.cents.infrastructure"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

room3 {
    // The database schema is exported here as JSON, one file per version. They are checked into git:
    // migrations are validated against them (and tested with them) later.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.room3.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    ksp(libs.androidx.room3.compiler)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // A fake HTTP server for the exchange-rate client's tests: a real socket on the host JVM, real HTTP.
    testImplementation(libs.okhttp.mockwebserver3)
    // A real SQLite for the JVM the unit tests run on: the Android driver only loads on a device. The
    // -jvm artifact is named explicitly, since asking for plain "sqlite-bundled" resolves to the Android one.
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    testRuntimeOnly(libs.junit.platform.launcher)
}
