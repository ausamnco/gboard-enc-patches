package dev.custom.gboardpatches.patches.haptics

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Standalone Morphe bytecode patch that introduces continuous haptic feedback
 * when holding down the backspace key during repeat deletion in Gboard.
 *
 * It hooks into Gboard's repeat key dispatch pipeline (`PointerTracker.q`), checks for
 * `KeyEvent.KEYCODE_DEL` (`0x43` / 67), resolves the pressed `SoftKeyView`, and triggers
 * a tactile pulse via Gboard's native `PressEffectPlayer` module.
 *
 * This guarantees full compliance with:
 * - System vibration toggle (Settings -> Sound & vibration -> Haptic feedback)
 * - Gboard user preference (Settings -> Preferences -> Haptic feedback on keypress)
 * - User-configured vibration duration / strength slider
 * - Safe fallback to `view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`
 */
val backspaceRepeatHapticsPatch = bytecodePatch(
    name = "Backspace Repeat Haptic Feedback",
    description = "Triggers continuous tactile feedback pulses when the backspace key is held down during repeated deletion.",
    default = true
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
        val targetMethod = RepeatKeyActionFingerprint.method
        val ownerClass = RepeatKeyActionFingerprint.classDef

        // 1. Dynamically discover SoftKeyView field and PressEffectPlayer bindings
        val softKeyViewField = PressEffectPlayerFinder.findSoftKeyViewFieldName(ownerClass)
        val playerBindings = PressEffectPlayerFinder.findPressEffectPlayer(ownerClass)

        val helperMethodName = "morpheBackspaceRepeatHaptic"
        val helperMethodExists = ownerClass.methods.any { it.name == helperMethodName }

        if (!helperMethodExists) {
            val hapticCallSmali = if (playerBindings != null) {
                """
                invoke-static {}, ${playerBindings.getterClass}->${playerBindings.getterMethod}()${playerBindings.playerInterface}
                move-result-object v1
                if-eqz v1, :cond_fallback
                const/4 v2, 0x0
                invoke-interface {v1, v0, v2}, ${playerBindings.playerInterface}->${playerBindings.playMethod}(Landroid/view/View;I)V
                return-void
                """.trimIndent()
            } else {
                """
                goto :cond_fallback
                """.trimIndent()
            }

            val helperBody = """
                if-eqz p1, :cond_return
                invoke-virtual {p1}, Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->b()Lpnu;
                move-result-object v0
                if-eqz v0, :cond_return
                iget v1, v0, Lpnu;->c:I
                const/16 v2, 0x43
                if-ne v1, v2, :cond_return
                iget-object v0, p0, ${ownerClass.type}->$softKeyViewField:Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;
                if-eqz v0, :cond_return
                :try_start_0
                $hapticCallSmali
                :try_end_0
                .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0
                :catch_0
                :cond_fallback
                const/4 v1, 0x3
                invoke-virtual {v0, v1}, Landroid/view/View;->performHapticFeedback(I)Z
                :cond_return
                return-void
            """.trimIndent()

            ownerClass.methods.add(
                ImmutableMethod(
                    ownerClass.type,
                    helperMethodName,
                    listOf(ImmutableMethodParameter("Lcom/google/android/libraries/inputmethod/metadata/ActionDef;", null, null)),
                    "V",
                    AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
                    null,
                    null,
                    MutableMethodImplementation(5)
                ).toMutable().apply {
                    addInstructions(0, helperBody)
                }
            )
        }

        // 2. Prepend invocation at index 0 of repeat dispatch method q
        targetMethod.addInstructions(
            0,
            """
            invoke-direct {p0, p1}, ${ownerClass.type}->$helperMethodName(Lcom/google/android/libraries/inputmethod/metadata/ActionDef;)V
            """.trimIndent()
        )
    }
}
