package app.gboard.colemak

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.w3c.dom.Document
import org.w3c.dom.Element

private val gboardCompatibility = Compatibility(
    name = "Gboard",
    packageName = "com.google.android.inputmethod.latin",
    targets = listOf(
        AppTarget(version = "18.5.4.993608717-release-arm64-v8a")
    ),
)

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val SETTINGS_ENTRY_KEY = "qwertz_custom_layout_entry"
private const val SETTINGS_ACTIVITY = "app.gboard.colemak.QwertzSettingsActivity"
private const val SETTINGS_HEADER_TAG =
    "com.google.android.libraries.inputmethod.settings.widget.HeaderPreference"

private fun Element.children(): List<Element> {
    val result = mutableListOf<Element>()
    for (index in 0 until childNodes.length) {
        val child = childNodes.item(index)
        if (child is Element) result += child
    }
    return result
}

private fun Element.androidAttribute(name: String): String {
    val namespaced = getAttributeNS(ANDROID_NS, name)
    return if (namespaced.isNotBlank()) namespaced else getAttribute("android:$name")
}

private fun Element.setAndroidAttribute(name: String, value: String) {
    setAttributeNS(ANDROID_NS, "android:$name", value)
}

private fun installSettingsActivity(manifestDocument: Document) {
    val manifest = manifestDocument.documentElement
    val application = manifest.children().firstOrNull { it.tagName == "application" }
        ?: error("Could not find application in Gboard AndroidManifest.xml")

    val activity = application.children().firstOrNull {
        it.tagName == "activity" && it.androidAttribute("name") == SETTINGS_ACTIVITY
    } ?: manifestDocument.createElement("activity").also(application::appendChild)

    activity.setAndroidAttribute("name", SETTINGS_ACTIVITY)
    activity.setAndroidAttribute("exported", "false")
    activity.setAndroidAttribute("label", "Keyboard Studio")
}

private fun installSettingsEntry(settingsDocument: Document) {
    val root = settingsDocument.documentElement
    val existing = root.children().flatMap { parent ->
        if (parent.tagName == "androidx.preference.PreferenceCategory") parent.children()
        else listOf(parent)
    }.firstOrNull { it.androidAttribute("key") == SETTINGS_ENTRY_KEY }
    if (existing != null) return

    val category = settingsDocument.createElement("androidx.preference.PreferenceCategory")
    category.setAndroidAttribute("title", "Keyboard Studio")

    val entry = settingsDocument.createElement(SETTINGS_HEADER_TAG)
    entry.setAndroidAttribute("persistent", "false")
    entry.setAndroidAttribute("selectable", "true")
    entry.setAndroidAttribute("title", "Keyboard Studio")
    entry.setAndroidAttribute("summary", "Efeitos das teclas e organização do layout")
    entry.setAndroidAttribute("key", SETTINGS_ENTRY_KEY)

    val intent = settingsDocument.createElement("intent")
    intent.setAndroidAttribute("action", "android.intent.action.MAIN")
    intent.setAndroidAttribute("targetPackage", "com.google.android.inputmethod.latin")
    intent.setAndroidAttribute("targetClass", SETTINGS_ACTIVITY)
    entry.appendChild(intent)
    category.appendChild(entry)

    val footer = root.children().firstOrNull {
        it.tagName == "com.android.settingslib.widget.FooterPreference"
    }
    if (footer != null) root.insertBefore(category, footer) else root.appendChild(category)
}

private val qwertzSettingsResourcePatch = resourcePatch(
    description = "Adds Keyboard Studio to Gboard settings for key effects and Latin layout editing.",
) {
    compatibleWith(gboardCompatibility)

    execute {
        document("AndroidManifest.xml").use(::installSettingsActivity)
        document("res/xml/settings.xml").use(::installSettingsEntry)
        document("res/xml/settings_legacy.xml").use(::installSettingsEntry)
    }
}

private fun mergeBundledExtension(context: BytecodePatchContext, extension: String) {
    val resourceClassLoader = object {}.javaClass.classLoader
    val runtimeClassLoader = context.javaClass.classLoader
    val extensionStream = resourceClassLoader.getResourceAsStream(extension)
        ?: error("QWERTZ settings extension not found: $extension")
    try {
        val extensionBytes = extensionStream.readBytes()
        val patchClasses = context.javaClass
            .getMethod("getPatchClasses\$morphe_patcher")
            .invoke(context)
        val classDefClass = Class.forName(
            "com.android.tools.smali.dexlib2.iface.ClassDef", false, runtimeClassLoader,
        )
        val extensionDex = loadExtensionDex(context, runtimeClassLoader, extensionBytes, extension)
        val extensionClasses = extensionDex.javaClass.getMethod("getClasses").invoke(extensionDex)
            as Iterable<*>
        val classByOrNull = patchClasses.javaClass.getMethod("classByOrNull", String::class.java)
        val addClass = patchClasses.javaClass.getMethod(
            "addClass\$morphe_patcher", classDefClass,
        )
        val mergerClass = Class.forName("app.morphe.patcher.util.ClassMerger", false, runtimeClassLoader)
        val merger = mergerClass.getField("INSTANCE").get(null)
        val merge = mergerClass.getMethod("merge", classDefClass, classDefClass, context.javaClass)

        for (classDef in extensionClasses) {
            if (classDef == null) continue
            val descriptor = classDefClass.getMethod("getType").invoke(classDef) as String
            val existing = classByOrNull.invoke(patchClasses, descriptor)
            if (existing == null) {
                addClass.invoke(patchClasses, classDef)
            } else {
                val merged = merge.invoke(merger, existing, classDef, context)
                if (merged !== existing) addClass.invoke(patchClasses, merged)
            }
        }
    } finally {
        extensionStream.close()
    }
}

private fun loadExtensionDex(
    context: BytecodePatchContext,
    runtimeClassLoader: ClassLoader,
    extensionBytes: ByteArray,
    extension: String,
): Any {
    val opcodes = context.javaClass.getMethod("getOpcodes\$morphe_patcher").invoke(context)
    val opcodesClass = Class.forName("com.android.tools.smali.dexlib2.Opcodes", false, runtimeClassLoader)
    val rawDexIoClass = runCatching {
        Class.forName("lanchon.multidexlib2.RawDexIO", false, runtimeClassLoader)
    }.getOrNull()
    if (rawDexIoClass != null) {
        val readMethod = runCatching {
            rawDexIoClass.getMethod("readRawDexFile", ByteArray::class.java, Integer.TYPE, opcodesClass)
        }.getOrNull()
        if (readMethod != null) {
            return readMethod.invoke(null, extensionBytes, 0, opcodes)
                ?: error("Could not decode QWERTZ extension DEX: $extension")
        }
    }

    val dexClass = Class.forName(
        "com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile", false, runtimeClassLoader,
    )
    val fromInputStream = dexClass.getMethod(
        "fromInputStream", opcodesClass, InputStream::class.java,
    )
    return ByteArrayInputStream(extensionBytes).use { stream ->
        fromInputStream.invoke(null, opcodes, stream)
            ?: error("Could not decode QWERTZ extension DEX: $extension")
    }
}

private val qwertzNativeRuntimeBytecodePatch = bytecodePatch(
    description = "Adds Keyboard Studio runtime hooks for key effects and Latin layout remapping.",
) {
    compatibleWith(gboardCompatibility)

    execute {
        mergeBundledExtension(this, "extensions/qwertz-settings.dex")

        val owner = mutableClassDefBy("Lqbi;")
        val method = owner.methods.singleOrNull {
            it.name == "a" && it.parameterTypes.isEmpty() &&
                it.returnType == "Lcom/google/android/libraries/inputmethod/metadata/KeyMappingDef;"
        } ?: error("Could not find the Gboard KeyMappingDef builder method Lqbi;->a()")

        val instructions = method.implementation?.instructions
            ?: error("Gboard KeyMappingDef builder has no implementation")
        val returnIndexes = instructions.indices.filter {
            instructions[it].opcode.name == "RETURN_OBJECT"
        }
        check(returnIndexes.size == 1) {
            "Expected one KeyMappingDef return instruction; found ${returnIndexes.size}"
        }
        val returnIndex = returnIndexes.single()
        val returnRegister = (instructions[returnIndex] as OneRegisterInstruction).registerA
        method.addInstructions(
            returnIndex,
            "invoke-static {v$returnRegister}, Lapp/gboard/colemak/QwertzLayoutRuntime;->apply(Ljava/lang/Object;)V",
        )

        // Hook Gboard's central keyboard touch dispatcher. The extension draws into
        // SoftKeyboardView's overlay and does not consume MotionEvent or alter input.
        val keyboardView = mutableClassDefBy(
            "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;"
        )
        val dispatchMethod = keyboardView.methods.singleOrNull {
            it.name == "dispatchTouchEvent" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes.single().toString() == "Landroid/view/MotionEvent;" &&
                it.returnType.toString() == "Z"
        } ?: error("Could not find SoftKeyboardView.dispatchTouchEvent(MotionEvent): boolean")

        dispatchMethod.addInstructions(
            0,
            "invoke-static {p0, p1}, Lapp/gboard/colemak/KeyboardEffectsHook;->onDispatchTouchEvent(Landroid/view/View;Landroid/view/MotionEvent;)V",
        )
    }
}

/** Public Morphe patch. Its editable settings live inside Gboard, not in Morphe options. */
@Suppress("unused")
val customizeQwertzLayoutPatch = resourcePatch(
    name = "Keyboard Studio: effects and custom layout",
    description = "Adds touch effects and visual drag-and-drop rearrangement for matching Latin alphabetic layouts inside Gboard settings.",
    default = true,
) {
    compatibleWith(gboardCompatibility)
    dependsOn(qwertzSettingsResourcePatch, qwertzNativeRuntimeBytecodePatch)
}
