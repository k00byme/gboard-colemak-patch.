package app.gboard.cafe

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch

private val gboardCompatibility = Compatibility(
    name = "Gboard",
    packageName = "com.google.android.inputmethod.latin",
    targets = listOf(
        AppTarget(version = "18.5.4.993608717-release-arm64-v8a"),
    ),
)

/**
 * Deliberately analysis-only. This confirms that the patch is being applied to
 * the intended Gboard build and inventories candidate settings/layout resources.
 * It does not claim to add UI or alter keyboard behavior yet.
 */
@Suppress("unused")
val gboardCafeDiscoveryPatch = resourcePatch(
    name = "Gboard Cafe: compatibility and resource inventory",
    description = "Checks the reference Gboard build and inventories settings and keyboard resources.",
    default = false,
) {
    compatibleWith(gboardCompatibility)

    execute {
        val entries = listApkEntries("")
        val candidates = entries.filter { path ->
            path.startsWith("res/xml/") ||
                path.startsWith("res/layout/") ||
                path.startsWith("res/drawable/")
        }.filter { path ->
            val lower = path.lowercase()
            lower.contains("setting") ||
                lower.contains("keyboard") ||
                lower.contains("key") ||
                lower.contains("theme")
        }.sorted()

        println("[Gboard Cafe] APK entries: ${entries.size}")
        println("[Gboard Cafe] Candidate UI resources:")
        candidates.forEach { println("  - $it") }

        check(entries.any { it == "AndroidManifest.xml" }) {
            "Input APK does not contain AndroidManifest.xml"
        }
    }
}
