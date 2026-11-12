package app.gboard.cafe

import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.resourcePatch

/** Optional diagnostic page exposed as its own Gboard Settings item. */
val gboardCafeDiagnosticsPatch = resourcePatch(
    name = "Diagnostics",
    description = "Adds a runtime status page for Cafe hooks and saved keyboard settings.",
    default = false,
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
                if (name == "app.gboard.cafe.runtime.GboardCafeDiagnosticsActivity") {
                    found = true
                    break
                }
            }
            if (!found) {
                val activity = manifest.createElement("activity")
                activity.setAttributeNS(
                    androidNs, "android:name",
                    "app.gboard.cafe.runtime.GboardCafeDiagnosticsActivity"
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
            val item = (0 until existing.length)
                .mapNotNull { existing.item(it) as? org.w3c.dom.Element }
                .firstOrNull { node ->
                    node.getAttributeNS(androidNs, "key") == "gboard_cafe_diagnostics"
                } ?: xml.createElement("Preference").also { root.appendChild(it) }
            run {
                val children = item.childNodes
                for (i in children.length - 1 downTo 0) {
                    val child = children.item(i)
                    if (child.nodeName == "intent") item.removeChild(child)
                }
            }
            item.setAttributeNS(androidNs, "android:key", "gboard_cafe_diagnostics")
            item.setAttributeNS(androidNs, "android:title", "Gboard Cafe Diagnostics")
            item.setAttributeNS(androidNs, "android:summary", "Status dos efeitos e do editor")
            item.setAttributeNS(androidNs, "android:order", "901")
            val intent = xml.createElement("intent")
            intent.setAttributeNS(androidNs, "android:action", "android.intent.action.MAIN")
            intent.setAttributeNS(
                androidNs, "android:targetPackage",
                "com.google.android.inputmethod.latin"
            )
            intent.setAttributeNS(
                androidNs, "android:targetClass",
                "app.gboard.cafe.runtime.GboardCafeDiagnosticsActivity"
            )
            item.appendChild(intent)
        }
    }
}
