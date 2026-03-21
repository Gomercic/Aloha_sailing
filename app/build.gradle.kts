import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.exists()
if (hasReleaseKeystore) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.example.startline"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.startline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: error("keystore.properties: missing keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: error("keystore.properties: missing keyPassword")
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: error("keystore.properties: missing storePassword")
                val storePath = keystoreProperties.getProperty("storeFile")
                    ?: error("keystore.properties: missing storeFile")
                val keystoreFile = rootProject.file(storePath)
                require(keystoreFile.exists()) {
                    "Keystore file not found: ${keystoreFile.absolutePath} (storeFile in keystore.properties)"
                }
                storeFile = keystoreFile
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

gradle.taskGraph.whenReady {
    val wantsSignedRelease = allTasks.any { task ->
        val p = task.path
        p.endsWith(":assembleRelease") || p.endsWith(":bundleRelease")
    }
    if (wantsSignedRelease && !hasReleaseKeystore) {
        throw GradleException(
            """
            Release signing is not configured.
            1) Copy keystore.properties.example to keystore.properties
            2) Create a keystore (see docs/RELEASE_SIGNING.md)
            3) Fill in storeFile, passwords, and keyAlias in keystore.properties
            """.trimIndent()
        )
    }
}