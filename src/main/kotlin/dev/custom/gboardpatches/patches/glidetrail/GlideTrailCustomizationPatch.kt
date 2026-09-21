package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/**
 * Standalone Morphe bytecode patch that enables deep customization of Gboard's glide typing trail.
 *
 * It allows adjusting:
 * - Color: Rainbow dynamic RGB cycling, or solid vivid colors (Neon Cyan, Electric Purple, Hot Pink,
 *   Vibrant Red, Neon Orange, Electric Green, Pure White, or Theme Default).
 * - Speed / Fade Duration: Fast (400ms), Normal (1000ms), Slow (2200ms), Ultra slow (4000ms ribbon).
 * - Width / Thickness: Thin (6dp), Normal (13dp), Thick (22dp), Extra thick (32dp).
 * - Length / Tail Decay: Short (compact 8-pt tail), Normal (~20-pt tail), Extended (60-pt tail),
 *   or Full stroke length without distance taper decay.
 *
 * Hook Points:
 * 1. `GestureOverlayView` (com.google.android.apps.inputmethod.libs.gestureui.GestureOverlayView):
 *    - Injects fields for fade duration, rainbow state, and stock parameter backups.
 *    - Injects `morpheApplyCustomTrailSettings()` and `morpheOnDrawHook()`.
 *    - Hooks `c(Context, AttributeSet)` right before return to capture stock values and apply prefs.
 *    - Hooks `onDraw(Canvas)` at index 0 to update real-time Rainbow RGB hue and refresh settings.
 * 2. `mvs.g` (Gesture path processor):
 *    - Replaces the hardcoded `const-wide/16 v13, 1000` instruction with:
 *      `sget-wide v13, Lcom/google/android/apps/inputmethod/libs/gestureui/GestureOverlayView;->morpheFadeDuration:J`
 *      which scales the alpha and stroke decay dynamically based on the configured duration.
 */
val glideTrailCustomizationPatch = bytecodePatch(
    name = "Glide Trail Customization",
    description = "Allows customizing glide typing trail color (including dynamic Rainbow RGB), fade speed, thickness, and length.",
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
    dependsOn(glideTrailSettingsPatch)

    execute {
        val overlayClass = mutableClassDefBy("Lcom/google/android/apps/inputmethod/libs/gestureui/GestureOverlayView;")

        // 1. Add fields to GestureOverlayView
        fun addFieldIfMissing(
            name: String,
            type: String,
            accessFlags: Int
        ) {
            if (overlayClass.fields.none { it.name == name }) {
                overlayClass.fields.add(
                    ImmutableField(
                        overlayClass.type,
                        name,
                        type,
                        accessFlags,
                        null,
                        null,
                        null
                    ).toMutable()
                )
            }
        }

        addFieldIfMissing("morpheFadeDuration", "J", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value)
        addFieldIfMissing("morpheStockColor", "I", AccessFlags.PUBLIC.value)
        addFieldIfMissing("morpheStockWidth", "I", AccessFlags.PUBLIC.value)
        addFieldIfMissing("morpheStockRetention", "I", AccessFlags.PUBLIC.value)
        addFieldIfMissing("morpheStockAlphaDecay", "F", AccessFlags.PUBLIC.value)
        addFieldIfMissing("morpheStockWidthDecay", "F", AccessFlags.PUBLIC.value)
        addFieldIfMissing("morpheLastPrefCheck", "J", AccessFlags.PUBLIC.value)
        addFieldIfMissing("morpheIsRainbow", "Z", AccessFlags.PUBLIC.value)
        addFieldIfMissing("morpheCustomApplied", "Z", AccessFlags.PUBLIC.value)

        // 2. Initialize morpheFadeDuration in GestureOverlayView.<clinit>
        val clinitMethod = overlayClass.methods.firstOrNull { it.name == "<clinit>" }
        if (clinitMethod == null) {
            val newClinit = ImmutableMethod(
                overlayClass.type,
                "<clinit>",
                emptyList(),
                "V",
                AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value,
                null,
                null,
                MutableMethodImplementation(2)
            ).toMutable()
            newClinit.addInstructions(
                0,
                """
                const-wide/16 v0, 0x3e8
                sput-wide v0, ${overlayClass.type}->morpheFadeDuration:J
                return-void
                """.trimIndent()
            )
            overlayClass.methods.add(newClinit)
        } else {
            val impl = clinitMethod.implementation
            if (impl != null) {
                if (impl.registerCount < 2) {
                    val regField = MutableMethodImplementation::class.java.getDeclaredField("registerCount")
                    regField.isAccessible = true
                    regField.setInt(impl, 2)
                }
                clinitMethod.addInstructions(
                    0,
                    """
                    const-wide/16 v0, 0x3e8
                    sput-wide v0, ${overlayClass.type}->morpheFadeDuration:J
                    """.trimIndent()
                )
            }
        }

        // 3. Add morpheOnDrawHook to GestureOverlayView
        val onDrawHookName = "morpheOnDrawHook"
        if (overlayClass.methods.none { it.name == onDrawHookName }) {
            val onDrawHookSmali = """
                # 1. If morpheIsRainbow is true, update hue on every animation frame
                iget-boolean v0, p0, ${overlayClass.type}->morpheIsRainbow:Z
                if-eqz v0, :cond_check_time
                invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J
                move-result-wide v0
                const-wide/16 v2, 0x6
                div-long/2addr v0, v2
                const-wide/16 v2, 0x168
                rem-long/2addr v0, v2
                long-to-float v0, v0

                const/4 v1, 0x3
                new-array v1, v1, [F
                const/4 v2, 0x0
                aput v0, v1, v2
                const/4 v0, 0x1
                const/high16 v2, 0x3f800000 # 1.0f
                aput v2, v1, v0
                const/4 v0, 0x2
                aput v2, v1, v0

                invoke-static {v1}, Landroid/graphics/Color;->HSVToColor([F)I
                move-result v0
                invoke-virtual {p0, v0}, ${overlayClass.type}->b(I)V

                :cond_check_time
                # 2. Check SharedPreferences at most once every 500ms
                invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J
                move-result-wide v0
                iget-wide v2, p0, ${overlayClass.type}->morpheLastPrefCheck:J
                sub-long v2, v0, v2
                const-wide/16 v4, 0x1f4 # 500ms
                cmp-long v2, v2, v4
                if-lez v2, :cond_return
                iput-wide v0, p0, ${overlayClass.type}->morpheLastPrefCheck:J
                invoke-direct {p0}, ${overlayClass.type}->morpheApplyCustomTrailSettings()V

                :cond_return
                return-void
            """.trimIndent()

            overlayClass.methods.add(
                ImmutableMethod(
                    overlayClass.type,
                    onDrawHookName,
                    emptyList(),
                    "V",
                    AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                    null,
                    null,
                    MutableMethodImplementation(10)
                ).toMutable().apply {
                    addInstructions(0, onDrawHookSmali)
                }
            )
        }

        // 4. Add morpheApplyCustomTrailSettings to GestureOverlayView
        val applySettingsMethodName = "morpheApplyCustomTrailSettings"
        if (overlayClass.methods.none { it.name == applySettingsMethodName }) {
            val applySettingsSmali = """
                invoke-virtual {p0}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v0
                if-nez v0, :cond_ctx_ok
                return-void

                :cond_ctx_ok
                invoke-virtual {v0}, Landroid/content/Context;->isDeviceProtectedStorage()Z
                move-result v1
                if-eqz v1, :cond_try_de
                move-object v1, v0
                goto :cond_read_de

                :cond_try_de
                invoke-virtual {v0}, Landroid/content/Context;->createDeviceProtectedStorageContext()Landroid/content/Context;
                move-result-object v1

                :cond_read_de
                if-eqz v1, :cond_fallback_ce
                invoke-static {v1}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;
                move-result-object v1
                if-eqz v1, :cond_fallback_ce
                goto :cond_prefs_ok

                :cond_fallback_ce
                invoke-static {v0}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;
                move-result-object v1
                if-nez v1, :cond_prefs_ok
                return-void

                :cond_prefs_ok
                # 1. Master Customization Toggle (default false)
                const-string v2, "pref_key_glide_trail_custom_enabled"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-nez v2, :cond_custom_enabled

                # Master toggle is OFF. Check if customizations were previously applied.
                iget-boolean v2, p0, ${overlayClass.type}->morpheCustomApplied:Z
                if-nez v2, :cond_revert_stock
                # Never customized, pure stock state. Ensure fade duration is stock 1000L and exit immediately.
                const-wide/16 v2, 0x3e8
                sput-wide v2, ${overlayClass.type}->morpheFadeDuration:J
                return-void

                :cond_revert_stock
                const/4 v2, 0x0
                iput-boolean v2, p0, ${overlayClass.type}->morpheCustomApplied:Z
                iput-boolean v2, p0, ${overlayClass.type}->morpheIsRainbow:Z
                const-wide/16 v2, 0x3e8
                sput-wide v2, ${overlayClass.type}->morpheFadeDuration:J

                iget v2, p0, ${overlayClass.type}->morpheStockWidth:I
                if-lez v2, :cond_skip_w
                iput v2, p0, ${overlayClass.type}->b:I
                :cond_skip_w

                iget v2, p0, ${overlayClass.type}->morpheStockRetention:I
                if-lez v2, :cond_skip_ret
                iput v2, p0, ${overlayClass.type}->d:I
                :cond_skip_ret

                iget v2, p0, ${overlayClass.type}->morpheStockAlphaDecay:F
                const/4 v3, 0x0
                cmpl-float v3, v2, v3
                if-lez v3, :cond_skip_ad
                iput v2, p0, ${overlayClass.type}->e:F
                :cond_skip_ad

                iget v2, p0, ${overlayClass.type}->morpheStockWidthDecay:F
                const/4 v3, 0x0
                cmpl-float v3, v2, v3
                if-lez v3, :cond_skip_wd
                iput v2, p0, ${overlayClass.type}->f:F
                :cond_skip_wd

                iget v2, p0, ${overlayClass.type}->morpheStockColor:I
                if-eqz v2, :cond_skip_color
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                :cond_skip_color

                return-void

                :cond_custom_enabled
                const/4 v2, 0x1
                iput-boolean v2, p0, ${overlayClass.type}->morpheCustomApplied:Z

                # 2. Rainbow Check
                const-string v2, "pref_key_glide_trail_rainbow"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                iput-boolean v2, p0, ${overlayClass.type}->morpheIsRainbow:Z
                if-eqz v2, :cond_check_cyan
                goto :cond_speed_check

                :cond_check_cyan
                const-string v2, "pref_key_glide_trail_color_cyan"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_check_purple
                const v2, -0xff1a01 # 0xFF00E5FF (Neon Cyan)
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                goto :cond_speed_check

                :cond_check_purple
                const-string v2, "pref_key_glide_trail_color_purple"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_check_pink
                const v2, -0x2aff07 # 0xFFD500F9 (Electric Purple)
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                goto :cond_speed_check

                :cond_check_pink
                const-string v2, "pref_key_glide_trail_color_pink"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_check_red
                const v2, -0xbf7f # 0xFFFF4081 (Hot Pink)
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                goto :cond_speed_check

                :cond_check_red
                const-string v2, "pref_key_glide_trail_color_red"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_check_orange
                const v2, -0xe8bc # 0xFFFF1744 (Vibrant Red)
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                goto :cond_speed_check

                :cond_check_orange
                const-string v2, "pref_key_glide_trail_color_orange"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_check_green
                const v2, -0x9300 # 0xFFFF6D00 (Neon Orange)
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                goto :cond_speed_check

                :cond_check_green
                const-string v2, "pref_key_glide_trail_color_green"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_check_white
                const v2, -0xff198a # 0xFF00E676 (Electric Green)
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                goto :cond_speed_check

                :cond_check_white
                const-string v2, "pref_key_glide_trail_color_white"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_check_stock_color
                const/4 v2, -0x1 # 0xFFFFFFFF (Pure White)
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V
                goto :cond_speed_check

                :cond_check_stock_color
                iget v2, p0, ${overlayClass.type}->morpheStockColor:I
                if-eqz v2, :cond_speed_check
                invoke-virtual {p0, v2}, ${overlayClass.type}->b(I)V

                # 3. Speed / Fade Duration
                :cond_speed_check
                const-string v2, "pref_key_glide_trail_speed_fast"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_speed_slow
                const-wide/16 v2, 0x190 # 400L (Fast)
                sput-wide v2, ${overlayClass.type}->morpheFadeDuration:J
                goto :cond_width_check

                :cond_speed_slow
                const-string v2, "pref_key_glide_trail_speed_slow"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_speed_ultraslow
                const-wide/16 v2, 0x898 # 2200L (Slow)
                sput-wide v2, ${overlayClass.type}->morpheFadeDuration:J
                goto :cond_width_check

                :cond_speed_ultraslow
                const-string v2, "pref_key_glide_trail_speed_ultraslow"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_speed_stock
                const-wide/16 v2, 0xfa0 # 4000L (Ultra slow)
                sput-wide v2, ${overlayClass.type}->morpheFadeDuration:J
                goto :cond_width_check

                :cond_speed_stock
                const-wide/16 v2, 0x3e8 # 1000L (Stock Normal)
                sput-wide v2, ${overlayClass.type}->morpheFadeDuration:J

                # 4. Width / Thickness
                :cond_width_check
                invoke-virtual {p0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
                move-result-object v2
                if-nez v2, :cond_res_ok
                goto :cond_length_check

                :cond_res_ok
                invoke-virtual {v2}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
                move-result-object v2
                if-nez v2, :cond_dm_ok
                goto :cond_length_check

                :cond_dm_ok
                iget v2, v2, Landroid/util/DisplayMetrics;->density:F

                const-string v3, "pref_key_glide_trail_width_thick"
                const/4 v4, 0x0
                invoke-interface {v1, v3, v4}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v3
                if-eqz v3, :cond_width_extra
                const/high16 v3, 0x41b00000 # 22.0f
                mul-float/2addr v3, v2
                float-to-int v2, v3
                iput v2, p0, ${overlayClass.type}->b:I
                goto :cond_length_check

                :cond_width_extra
                const-string v3, "pref_key_glide_trail_width_extrathick"
                const/4 v4, 0x0
                invoke-interface {v1, v3, v4}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v3
                if-eqz v3, :cond_width_thin
                const/high16 v3, 0x42000000 # 32.0f
                mul-float/2addr v3, v2
                float-to-int v2, v3
                iput v2, p0, ${overlayClass.type}->b:I
                goto :cond_length_check

                :cond_width_thin
                const-string v3, "pref_key_glide_trail_width_thin"
                const/4 v4, 0x0
                invoke-interface {v1, v3, v4}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v3
                if-eqz v3, :cond_width_stock
                const/high16 v3, 0x40c00000 # 6.0f
                mul-float/2addr v3, v2
                float-to-int v2, v3
                iput v2, p0, ${overlayClass.type}->b:I
                goto :cond_length_check

                :cond_width_stock
                iget v2, p0, ${overlayClass.type}->morpheStockWidth:I
                if-lez v2, :cond_length_check
                iput v2, p0, ${overlayClass.type}->b:I

                # 5. Length / Decay
                :cond_length_check
                const-string v2, "pref_key_glide_trail_length_long"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_length_infinite
                const/16 v2, 0x3c # 60 points
                iput v2, p0, ${overlayClass.type}->d:I
                const v2, 0x3dcccccd # 0.1f
                iput v2, p0, ${overlayClass.type}->e:F
                const v2, 0x3d4ccccd # 0.05f
                iput v2, p0, ${overlayClass.type}->f:F
                goto :cond_finish

                :cond_length_infinite
                const-string v2, "pref_key_glide_trail_length_infinite"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_length_short
                const/16 v2, 0x7d0 # 2000 points (full stroke retention)
                iput v2, p0, ${overlayClass.type}->d:I
                const/4 v2, 0x0
                iput v2, p0, ${overlayClass.type}->e:F
                iput v2, p0, ${overlayClass.type}->f:F
                goto :cond_finish

                :cond_length_short
                const-string v2, "pref_key_glide_trail_length_short"
                const/4 v3, 0x0
                invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v2
                if-eqz v2, :cond_length_stock
                const/16 v2, 0x8 # 8 points
                iput v2, p0, ${overlayClass.type}->d:I
                const/high16 v2, 0x3f800000 # 1.0f
                iput v2, p0, ${overlayClass.type}->e:F
                const/high16 v2, 0x3f000000 # 0.5f
                iput v2, p0, ${overlayClass.type}->f:F
                goto :cond_finish

                :cond_length_stock
                iget v2, p0, ${overlayClass.type}->morpheStockRetention:I
                if-lez v2, :cond_finish
                iput v2, p0, ${overlayClass.type}->d:I
                iget v2, p0, ${overlayClass.type}->morpheStockAlphaDecay:F
                iput v2, p0, ${overlayClass.type}->e:F
                iget v2, p0, ${overlayClass.type}->morpheStockWidthDecay:F
                iput v2, p0, ${overlayClass.type}->f:F

                :cond_finish
                return-void
            """.trimIndent()

            overlayClass.methods.add(
                ImmutableMethod(
                    overlayClass.type,
                    applySettingsMethodName,
                    emptyList(),
                    "V",
                    AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
                    null,
                    null,
                    MutableMethodImplementation(15)
                ).toMutable().apply {
                    addInstructions(0, applySettingsSmali)
                }
            )
        }

        // 5. Hook GestureOverlayView.c(Context, AttributeSet)
        val initMethod = overlayClass.methods.firstOrNull { m ->
            m.name == "c" && m.parameterTypes.size == 2 &&
            m.parameterTypes[0] == "Landroid/content/Context;" &&
            m.parameterTypes[1] == "Landroid/util/AttributeSet;"
        } ?: error("Method c(Context, AttributeSet) not found in GestureOverlayView")

        val initImpl = initMethod.implementation ?: error("No implementation in GestureOverlayView.c")

        // 5a. Save stock color before b(I)V call
        val bCallIndex = initImpl.instructions.indexOfLast { ins ->
            ins.opcode == Opcode.INVOKE_VIRTUAL &&
            (ins as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference?.toString()?.contains("->b(I)V") == true
        }
        if (bCallIndex >= 0) {
            initMethod.addInstructions(
                bCallIndex,
                """
                iput v0, p0, ${overlayClass.type}->morpheStockColor:I
                """.trimIndent()
            )
        }

        // 5b. Save stock width, retention, and decay rates right before return-void
        val returnVoidIndex = initImpl.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        if (returnVoidIndex >= 0) {
            val backupSmali = """
                iget v0, p0, ${overlayClass.type}->b:I
                iput v0, p0, ${overlayClass.type}->morpheStockWidth:I
                iget v0, p0, ${overlayClass.type}->d:I
                iput v0, p0, ${overlayClass.type}->morpheStockRetention:I
                iget v0, p0, ${overlayClass.type}->e:F
                iput v0, p0, ${overlayClass.type}->morpheStockAlphaDecay:F
                iget v0, p0, ${overlayClass.type}->f:F
                iput v0, p0, ${overlayClass.type}->morpheStockWidthDecay:F
            """.trimIndent()
            initMethod.addInstructions(returnVoidIndex, backupSmali)
        }

        // 6. Hook GestureOverlayView.onDraw(Canvas) at index 0
        val onDrawMethod = overlayClass.methods.firstOrNull { m ->
            m.name == "onDraw" && m.parameterTypes.size == 1 &&
            m.parameterTypes[0] == "Landroid/graphics/Canvas;"
        } ?: error("Method onDraw(Canvas) not found in GestureOverlayView")

        onDrawMethod.addInstructions(
            0,
            """
            invoke-direct {p0}, ${overlayClass.type}->morpheOnDrawHook()V
            """.trimIndent()
        )

        // 7. Hook mvs.g (Gesture path processor) to use dynamic fade duration with zero-division safeguard
        val mvsField = overlayClass.fields.firstOrNull { field ->
            val cls = classDefByOrNull(field.type)
            cls != null && cls.methods.any { m ->
                m.name == "g" && m.returnType == "Z" && m.parameterTypes.size == 3 &&
                m.parameterTypes[0] == "Ljava/util/List;" && m.parameterTypes[2] == "J"
            }
        }
        val mvsType = mvsField?.type ?: "Lmvs;"
        val mvsClass = mutableClassDefBy(mvsType)
        val gMethod = mvsClass.methods.firstOrNull { m ->
            m.name == "g" && m.returnType == "Z" && m.parameterTypes.size == 3 &&
            m.parameterTypes[0] == "Ljava/util/List;" && m.parameterTypes[2] == "J"
        } ?: error("Method g not found in processor class $mvsType")

        val gImpl = gMethod.implementation ?: error("No implementation in $mvsType.g")
        val gInstructions = gImpl.instructions.toList()
        val const1000Index = gInstructions.indexOfFirst { ins ->
            ins.opcode == Opcode.CONST_WIDE_16 && (ins as? WideLiteralInstruction)?.wideLiteral == 1000L
        }
        if (const1000Index >= 0) {
            val ins = gInstructions[const1000Index] as OneRegisterInstruction
            val targetReg = ins.registerA
            gMethod.removeInstruction(const1000Index)
            gMethod.addInstructions(
                const1000Index,
                """
                sget-wide v$targetReg, ${overlayClass.type}->morpheFadeDuration:J
                const-wide/16 v15, 0x190
                invoke-static {v$targetReg, v15}, Ljava/lang/Math;->max(JJ)J
                move-result-wide v$targetReg
                """.trimIndent()
            )
        }
    }
}
