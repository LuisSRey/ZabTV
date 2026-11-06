plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tvplayer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tvplayer.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Firma release vía env (keystore local, NUNCA en git — ver .gitignore).
    // Sin env, el APK release sale sin firmar (R8 sí se verifica igual).
    val releaseKeystorePath = System.getenv("TVPLAYER_KEYSTORE_PATH")
    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("TVPLAYER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TVPLAYER_KEY_ALIAS")
                keyPassword = System.getenv("TVPLAYER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

        buildFeatures {
        viewBinding = true
        aidl = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.coil)
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.paging.testing)
    testImplementation("androidx.test:core-ktx:1.7.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
