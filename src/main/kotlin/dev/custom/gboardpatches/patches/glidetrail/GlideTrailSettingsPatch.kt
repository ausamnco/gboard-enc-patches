package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val SETTING_GESTURE_XML = "res/xml/setting_gesture.xml"

// Master Toggle
const val PREF_KEY_GLIDE_TRAIL_CUSTOM_ENABLED = "pref_key_glide_trail_custom_enabled"

// Color & Rainbow
const val PREF_KEY_GLIDE_TRAIL_RAINBOW = "pref_key_glide_trail_rainbow"
const val PREF_KEY_GLIDE_TRAIL_COLOR_CYAN = "pref_key_glide_trail_color_cyan"
const val PREF_KEY_GLIDE_TRAIL_COLOR_PURPLE = "pref_key_glide_trail_color_purple"
const val PREF_KEY_GLIDE_TRAIL_COLOR_PINK = "pref_key_glide_trail_color_pink"
const val PREF_KEY_GLIDE_TRAIL_COLOR_RED = "pref_key_glide_trail_color_red"
const val PREF_KEY_GLIDE_TRAIL_COLOR_ORANGE = "pref_key_glide_trail_color_orange"
const val PREF_KEY_GLIDE_TRAIL_COLOR_GREEN = "pref_key_glide_trail_color_green"
const val PREF_KEY_GLIDE_TRAIL_COLOR_WHITE = "pref_key_glide_trail_color_white"

// Speed / Fade Duration
const val PREF_KEY_GLIDE_TRAIL_SPEED_FAST = "pref_key_glide_trail_speed_fast"
const val PREF_KEY_GLIDE_TRAIL_SPEED_SLOW = "pref_key_glide_trail_speed_slow"
const val PREF_KEY_GLIDE_TRAIL_SPEED_ULTRASLOW = "pref_key_glide_trail_speed_ultraslow"

// Width / Thickness
const val PREF_KEY_GLIDE_TRAIL_WIDTH_THIN = "pref_key_glide_trail_width_thin"
const val PREF_KEY_GLIDE_TRAIL_WIDTH_THICK = "pref_key_glide_trail_width_thick"
const val PREF_KEY_GLIDE_TRAIL_WIDTH_EXTRATHICK = "pref_key_glide_trail_width_extrathick"

// Length / Tail Decay
const val PREF_KEY_GLIDE_TRAIL_LENGTH_SHORT = "pref_key_glide_trail_length_short"
const val PREF_KEY_GLIDE_TRAIL_LENGTH_LONG = "pref_key_glide_trail_length_long"
const val PREF_KEY_GLIDE_TRAIL_LENGTH_INFINITE = "pref_key_glide_trail_length_infinite"

/**
 * Resource patch that injects comprehensive glide typing trail customization options
 * into Gboard Settings under Glide typing (res/xml/setting_gesture.xml).
 */
val glideTrailSettingsPatch = resourcePatch(
    description = "Adds glide typing trail customization preferences to Gboard Settings."
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(
                AppTarget(version = "18.0.3.954559732-release-arm64-v8a"),
                AppTarget(version = "18.0.3"),
                AppTarget(version = null, isExperimental = true)
            )
        )
    )

    execute {
        val document = document(SETTING_GESTURE_XML)
        try {
            applyGlideTrailSettingsPatch(document)
        } finally {
            document.close()
        }
    }
}

internal fun applyGlideTrailSettingsPatch(document: Document) {
    val root = document.documentElement ?: return

    // Avoid duplicate insertions
    if (findPreferenceByKey(root, PREF_KEY_GLIDE_TRAIL_CUSTOM_ENABLED) != null) {
        return
    }

    // 1. Locate the master "Glide trail" preference (key contains 0x7f140a54 or pref_gesture_preview_trail)
    var gestureTrailPref: Element? = null
    val allElements = getAllElements(root)
    for (el in allElements) {
        val key = el.getAndroidAttr("key")
        if (key.contains("0x7f140a54") || key.contains("pref_gesture_preview_trail")) {
            gestureTrailPref = el
            break
        }
    }

    // Fallback if key not found: find 2nd child in PreferenceScreen
    val anchorNode = gestureTrailPref ?: root.childNodes.run {
        var count = 0
        var found: Element? = null
        for (i in 0 until length) {
            val item = item(i)
            if (item is Element) {
                count++
                if (count == 2) {
                    found = item
                    break
                }
            }
        }
        found
    }

    val dependencyKey = gestureTrailPref?.getAndroidAttr("key")?.takeIf { it.isNotEmpty() }
        ?: "@string/_0_resource_name_obfuscated_res_0x7f140a54"

    fun createSwitch(
        key: String,
        title: String,
        summary: String,
        defaultValue: String = "false",
        dependency: String? = dependencyKey
    ): Element {
        return document.createElement("SwitchPreferenceCompat").apply {
            setAttributeNS(ANDROID_NS, "android:persistent", "true")
            setAttributeNS(ANDROID_NS, "android:title", title)
            setAttributeNS(ANDROID_NS, "android:summary", summary)
            setAttributeNS(ANDROID_NS, "android:key", key)
            setAttributeNS(ANDROID_NS, "android:defaultValue", defaultValue)
            if (!dependency.isNullOrEmpty()) {
                setAttributeNS(ANDROID_NS, "android:dependency", dependency)
            }
        }
    }

    fun createCategory(title: String): Element {
        return document.createElement("androidx.preference.PreferenceCategory").apply {
            setAttributeNS(ANDROID_NS, "android:title", title)
        }
    }

    // Prepare all customization elements
    val elementsToInsert = mutableListOf<Element>()

    // Master Customization Switch
    elementsToInsert.add(
        createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_CUSTOM_ENABLED,
            title = "Customize glide trail",
            summary = "Adjust glide typing trail color, speed, width, and length",
            defaultValue = "true"
        )
    )

    // Category: Trail Effects & Color
    val colorCategory = createCategory("Trail Effects & Color").apply {
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_RAINBOW,
            title = "Rainbow RGB effect",
            summary = "Continuously cycle vibrant rainbow colors across the spectrum while gliding"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_COLOR_CYAN,
            title = "Neon Cyan",
            summary = "Vibrant electric cyan glow (#00E5FF)"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_COLOR_PURPLE,
            title = "Electric Purple",
            summary = "Deep ultraviolet purple glow (#D500F9)"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_COLOR_PINK,
            title = "Hot Pink",
            summary = "Vivid neon pink glow (#FF4081)"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_COLOR_RED,
            title = "Vibrant Red",
            summary = "High-contrast crimson glow (#FF1744)"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_COLOR_ORANGE,
            title = "Neon Orange",
            summary = "Fiery amber orange glow (#FF6D00)"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_COLOR_GREEN,
            title = "Electric Green",
            summary = "Bright emerald green glow (#00E676)"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_COLOR_WHITE,
            title = "Pure White",
            summary = "Clean minimalist white glow (#FFFFFF)"
        ))
    }
    elementsToInsert.add(colorCategory)

    // Category: Trail Speed & Fade Duration
    val speedCategory = createCategory("Trail Speed & Fade Duration").apply {
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_SPEED_FAST,
            title = "Fast fade (400ms)",
            summary = "Trail fades quickly for a snappy, responsive feel"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_SPEED_SLOW,
            title = "Slow fade (2200ms)",
            summary = "Trail lingers longer across the keyboard"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_SPEED_ULTRASLOW,
            title = "Ultra slow fade (4000ms)",
            summary = "Extended ribbon mode with long lingering visibility"
        ))
    }
    elementsToInsert.add(speedCategory)

    // Category: Trail Width & Thickness
    val widthCategory = createCategory("Trail Width & Thickness").apply {
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_WIDTH_THIN,
            title = "Thin trail (6dp)",
            summary = "Delicate, precision hairline stroke"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_WIDTH_THICK,
            title = "Thick trail (22dp)",
            summary = "Wide, bold stroke for high visibility"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_WIDTH_EXTRATHICK,
            title = "Extra thick trail (32dp)",
            summary = "Heavy glowing ribbon effect"
        ))
    }
    elementsToInsert.add(widthCategory)

    // Category: Trail Length & Tail Decay
    val lengthCategory = createCategory("Trail Length & Tail Decay").apply {
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_LENGTH_SHORT,
            title = "Short trail tail",
            summary = "Compact tail following close behind your fingertip"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_LENGTH_LONG,
            title = "Extended trail length",
            summary = "Maintains thickness further behind finger before tapering"
        ))
        appendChild(createSwitch(
            key = PREF_KEY_GLIDE_TRAIL_LENGTH_INFINITE,
            title = "Full stroke length (No taper decay)",
            summary = "Prevents distance tapering so the full path stays visible until time fade"
        ))
    }
    elementsToInsert.add(lengthCategory)

    // Insert all created elements right after anchorNode
    val parent = anchorNode?.parentNode ?: root
    var insertReference = anchorNode?.nextSibling

    for (element in elementsToInsert) {
        if (insertReference == null) {
            parent.appendChild(element)
        } else {
            parent.insertBefore(element, insertReference)
        }
    }
}

private fun getAllElements(root: Element): List<Element> {
    val list = mutableListOf<Element>()
    fun traverse(node: Element) {
        list.add(node)
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                traverse(child)
            }
        }
    }
    traverse(root)
    return list
}

private fun findPreferenceByKey(root: Element, targetKey: String): Element? {
    return getAllElements(root).firstOrNull { it.getAndroidAttr("key") == targetKey }
}

private fun Element.getAndroidAttr(localName: String): String {
    val nsVal = getAttributeNS(ANDROID_NS, localName)
    if (nsVal.isNotEmpty()) return nsVal
    val direct = getAttribute("android:$localName")
    if (direct.isNotEmpty()) return direct
    for (i in 0 until attributes.length) {
        val attr = attributes.item(i)
        if (attr.localName == localName || attr.nodeName.substringAfterLast(':') == localName) {
            return attr.nodeValue ?: ""
        }
    }
    return ""
}
