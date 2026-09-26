import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing data, kept outside the code (see keystore.properties.example).
// If the file does not exist, an unsigned release APK is built.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.itools.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.itools.app"
        minSdk = 29          // Android 10
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        vectorDrawables { useSupportLibrary = true }
    }

    androidResources {
        generateLocaleConfig = true   // uses res/resources.properties
        localeFilters += listOf("en", "es")   // drop the ~90 other languages bundled by the dependencies
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                // BouncyCastle data files for unused features (post-quantum tables, PKIX message bundles)
                "org/bouncycastle/pqc/**", "org/bouncycastle/**/*.properties"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Camera (Camera recorder)
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Tool libraries
    implementation("com.google.zxing:core:3.5.3")                       // QR
    implementation("org.mindrot:jbcrypt:0.4")                           // Bcrypt
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")              // Hash / HMAC / SHA3 / RIPEMD160
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")              // PDF signature (CMS)
    implementation("org.snakeyaml:snakeyaml-engine:2.8")                // YAML
    implementation("org.tomlj:tomlj:1.1.1")                             // TOML
    implementation("org.commonmark:commonmark:0.24.0")                  // Markdown
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.52") // Phone numbers
    implementation("cash.z.ecc.android:kotlin-bip39:1.0.8")             // BIP39
}
