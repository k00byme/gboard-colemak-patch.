group = "org.k00byme"

patches {
    about {
        name = "Samsung Keyboard patches"
        description = "Experimental Morphe patches for Samsung Keyboard: Portuguese accent-only correction and planned toolbar and suggestion-position features."
        source = "https://github.com/k00byme/gboard-colemak-patch."
        author = "k00byme"
        contact = "https://github.com/k00byme"
        website = "https://github.com/k00byme/gboard-colemak-patch./tree/samsung-keyboard/samsung-keyboard"
        license = "GPLv3"
    }
}

dependencies {
    compileOnly(libs.gson)
}
