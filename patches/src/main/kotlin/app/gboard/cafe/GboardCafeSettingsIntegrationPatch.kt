package app.gboard.cafe

import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.resourcePatch

/** Adds the main Cafe entry and registers its Activity in Gboard's own package. */
val gboardCafeSettingsIntegrationPatch = resourcePatch(
    name = "Settings Integration",
    description = "Adds Gboard Cafe to Gboard's own settings screen.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    dependsOn(gboardCafeCorePatch)

    execute {
        val androidNs = "http://schemas.android.com/apk/res/android"
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0)
                ?: error("Gboard application element not found")
            val activities = manifest.getElementsByTagName("activity")
            var found = false
            for (i in 0 until activities.length) {
                val node = activities.item(i)
                val name = node.attributes?.getNamedItemNS(androidNs, "name")?.nodeValue
                if (name == "app.gboard.cafe.runtime.GboardCafeSettingsActivity") {
                    found = true
                    break
                }
            }
            if (!found) {
                val activity = manifest.createElement("activity")
                activity.setAttributeNS(
                    androidNs, "android:name",
                    "app.gboard.cafe.runtime.GboardCafeSettingsActivity"
                )
                activity.setAttributeNS(androidNs, "android:exported", "false")
                activity.setAttributeNS(androidNs, "android:excludeFromRecents", "true")
                activity.setAttributeNS(
                    androidNs, "android:theme",
                    "@android:style/Theme.Material.Light.NoActionBar"
                )
                application.appendChild(activity)
            }
        }

        document("res/0CM.xml").use { xml ->
            val root = xml.documentElement
            val existing = root.getElementsByTagName("Preference")
            var item = (0 until existing.length)
                .map { existing.item(it) }
                .firstOrNull {
                    it.attributes?.getNamedItemNS(androidNs, "key")?.nodeValue
                        == "gboard_cafe_settings"
                }

            if (item == null) {
                item = xml.createElement("Preference")
                root.appendChild(item)
            } else {
                val children = item.childNodes
                for (i in children.length - 1 downTo 0) {
                    val child = children.item(i)
                    if (child.nodeName == "intent") item.removeChild(child)
                }
            }

            item.setAttributeNS(androidNs, "android:key", "gboard_cafe_settings")
            item.setAttributeNS(androidNs, "android:title", "Gboard Cafe")
            item.setAttributeNS(androidNs, "android:summary", "Efeitos e organização das teclas")
            item.setAttributeNS(androidNs, "android:order", "900")

            val intent = xml.createElement("intent")
            intent.setAttributeNS(androidNs, "android:action", "android.intent.action.MAIN")
            intent.setAttributeNS(
                androidNs, "android:targetPackage",
                "com.google.android.inputmethod.latin"
            )
            intent.setAttributeNS(
                androidNs, "android:targetClass",
                "app.gboard.cafe.runtime.GboardCafeSettingsActivity"
            )
            item.appendChild(intent)
        }
    }
}
