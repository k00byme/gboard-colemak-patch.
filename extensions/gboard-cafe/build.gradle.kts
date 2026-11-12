extension {
    name = "extensions/gboard-cafe/gboard-cafe.mpe"
}

android {
    namespace = "app.gboard.cafe.runtime"
    compileSdk = 36

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
