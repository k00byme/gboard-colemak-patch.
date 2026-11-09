group = "app.gboard.colemak"

patches {
    about {
        name = "Gboard QWERTZ: native custom layout"
        description = "Adds a native settings category inside Gboard to customize the 10/9/7 QWERTZ letter rows."
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
