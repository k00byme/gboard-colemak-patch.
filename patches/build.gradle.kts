group = "app.gboard.colemak"

patches {
    about {
        name = "Gboard: all layouts native custom layout"
        description = "Adds a native Gboard settings category to customize the 10/9/7 Latin letter rows across matching layouts."
        source = "https://github.com/k00byme/gboard-colemak-patch."
        author = "k00byme"
        contact = "https://github.com/k00byme"
        website = "https://github.com/k00byme/gboard-colemak-patch."
        license = "GPLv3"
    }
}

dependencies {
    compileOnly(libs.gson)
}
