group = "app.gboard.colemak"

patches {
    about {
        name = "Gboard Colemak: remove semicolon and fill row"
        description = "Removes the semicolon from the Colemak main keyboard and expands the nine remaining letter keys to fill the row. The ?123 keyboard is unchanged."
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
