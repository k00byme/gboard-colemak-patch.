group = "app.gboard.cafe"

patches {
    about {
        name = "Gboard Cafe"
        description = "Rebuilds selected Keys Cafe-inspired effects and keyboard layout editing for Gboard."
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
