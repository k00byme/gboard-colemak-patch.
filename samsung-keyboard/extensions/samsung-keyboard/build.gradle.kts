extension {
    name = "extensions/samsung-keyboard/samsung-keyboard.mpe"
}

android {
    namespace = "org.k00byme.samsungkeyboard.runtime"
    compileSdk = 36

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
