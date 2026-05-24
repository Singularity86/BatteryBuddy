plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.batterybuddy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.batterybuddy"
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 1
        versionName   = "1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Export Room schema JSON for migration auditing — keep out of VCS via .gitignore
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.workmanager.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.commons.csv)
    implementation(libs.browser)
}
