plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dany.salahsilence"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dany.salahsilence"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // Core Android components
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    
    // Location services
    implementation("com.google.android.gms:play-services-location:21.2.0")
    
    // Retrofit for API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Splash screen API
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // CardView for prayer time cards
    implementation("androidx.cardview:cardview:1.0.0")
    
    // WorkManager for background tasks
    implementation("androidx.work:work-runtime:2.9.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
