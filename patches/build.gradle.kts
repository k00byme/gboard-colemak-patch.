group = "app.gboard.colemak"

patches {
    about {
        name = "Gboard QWERTZ: custom layout"
        description = "Adds three Morphe options to customize the QWERTZ letter rows: 10 keys on the top row, 9 on the middle row and 7 on the bottom row. All 26 letters must be used exactly once."
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
