plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "pe.atena.mic"
    compileSdk = 35
    defaultConfig {
        applicationId = "pe.atena.miccontrol"
        minSdk = 33
        targetSdk = 35
        versionCode = 14
        versionName = "1.4-wash-sd-voz"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
