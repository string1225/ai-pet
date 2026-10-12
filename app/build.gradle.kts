plugins { id("com.android.application") }

android {
    namespace = "com.string.aipet"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.string.aipet"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = providers.gradleProperty("instrumentationRunner").orElse("com.string.aipet.FamilyInstrumentation").get()
        versionCode = providers.gradleProperty("appVersionCode").orElse("3").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.2.0").get()
        buildConfigField("String", "UPDATE_TEST_URL", "\"\"")
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("fixedRelease") {
            // Preserve the v0.1.0 certificate; keep the keystore outside Git.
            storeFile = file(System.getenv("AI_PET_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/ai-pet-release.keystore")
            storePassword = System.getenv("AI_PET_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("AI_PET_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("AI_PET_KEY_PASSWORD") ?: "android"
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("fixedRelease") }
        getByName("debug") {
            val fixture = providers.environmentVariable("AI_PET_UPDATE_TEST_URL").orElse("").get()
            require(fixture.isEmpty() || fixture.matches(Regex("http://(127\\.0\\.0\\.1|10\\.0\\.2\\.2):[0-9]+/latest"))) { "Update fixture must be an emulator loopback URL" }
            buildConfigField("String", "UPDATE_TEST_URL", "\"$fixture\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

dependencies { testImplementation("junit:junit:4.13.2") }
