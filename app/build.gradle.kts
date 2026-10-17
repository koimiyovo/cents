import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

// The address a crash report is sent to: `supportEmail` in local.properties (never versioned). Without it the
// e-mail opens with no recipient and the user chooses one.
val localFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localFile.exists())
{
    localFile.inputStream().use { localProperties.load(it) }
}
val supportEmail = localProperties.getProperty("supportEmail", "")

android {
    namespace = "com.kyovo.cents"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kyovo.cents"
        minSdk = 30
        targetSdk = 37
        versionCode = 6
        versionName = "1.0"
        buildConfigField("String", "SUPPORT_EMAIL", "\"$supportEmail\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The upload key stays out of git: keystore.properties (storeFile, storePassword, keyAlias,
    // keyPassword) at the project root. Without it the release build is simply left unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystoreFile.exists())
    {
        keystoreFile.inputStream().use { keystoreProperties.load(it) }
    }

    signingConfigs {
        if (keystoreFile.exists())
        {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}