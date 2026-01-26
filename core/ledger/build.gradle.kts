plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.midnight.kuira.core.ledger"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)

    // Coroutines (for suspend functions in future phases)
    implementation(libs.kotlinx.coroutines.android)

    // Ktor for HTTP client (Node RPC submission - Phase 2E)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Kotlinx Serialization for JSON
    implementation(libs.kotlinx.serialization.json)

    // Dependency Injection
    implementation("javax.inject:javax.inject:1")

    // Crypto module (for TransactionSigner native library)
    implementation(project(":core:crypto"))

    // Indexer module (for UTXO management and selection)
    implementation(project(":core:indexer"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("io.mockk:mockk:1.13.8")  // MockK for Kotlin
    testImplementation("io.ktor:ktor-client-mock:2.3.7")
    testImplementation(project(":core:testing"))

    // Android Instrumentation Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("io.mockk:mockk-android:1.13.8")  // MockK for Android
    androidTestImplementation(project(":core:indexer"))
    androidTestImplementation(project(":core:testing"))
}
