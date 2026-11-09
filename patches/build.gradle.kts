group = "app.gboard.colemak"

patches {
    about {
        name = "k00byme Gboard Colemak Patch"
        description = "A focused Gboard patch that removes the semicolon key from the Colemak main keyboard."
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
