# Gboard Enhancement Patches Suite

<p align="center">
  <a href="https://morphe.software/add-source?github=ausamnco/gboard-enc-patches"><img alt="Add to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Source-00A8FF?style=for-the-badge"></a>
  <img alt="Gboard" src="https://img.shields.io/badge/Target-Gboard-4285F4?style=for-the-badge">
  <img alt="License" src="https://img.shields.io/badge/License-GPLv3-green?style=for-the-badge">
</p>

A standalone, Morphe/ReVanced-compatible bytecode and resource patch suite for **Gboard** (`com.google.android.inputmethod.latin`).

This repository provides two independent patches that can be selected individually or together in the **Morphe** app alongside other third-party patch sources:

1. **Backspace Repeat Haptic Feedback**: Introduces continuous tactile feedback pulses when the backspace key is held down during repeated character deletion (with empty text suppression and a dedicated settings toggle).
2. **Enter Key Tasker Event**: Broadcasts a high-priority Android Intent event to **Tasker** whenever the Enter key or bottom-right IME Action key is pressed, including active app package name, action type, raw keycode, and preceding text.

---

## 📱 Quick Setup: Use in Morphe App

1. **Add Repository Source**:
   - Open **Morphe** on your phone.
   - Go to **Settings** ⚙️ -> **Sources**.
   - Tap **Add Source** and enter:
     ```
     https://github.com/ausamnco/gboard-enc-patches
     ```
   - Alternatively, tap the **Add to Morphe** button above from your Android browser.

2. **Patch Gboard**:
   - In Morphe, navigate to the **Patcher** tab.
   - Select **Gboard** (`18.0.3.954559732-release-arm64-v8a` or compatible).
   - In the patch selection list, you will see both patches:
     - ☑️ **Backspace Repeat Haptic Feedback**
     - ☑️ **Enter Key Tasker Event**
   - Select the patches you desire and tap **Patch**!
   - Install the generated APK.

---

## ⚡ Patch 1: Backspace Repeat Haptic Feedback

- **How it works:** Hooks into Gboard's repeat key dispatch pipeline. While backspace is held down, every deletion step fires a tactile pulse via Gboard's native haptic player.
- **Smart suppression:** If the cursor is at the beginning of a line/field and no text is deleted, vibration is automatically suppressed.
- **Gboard Settings:** Adds a toggle under *Settings -> Preferences -> Key tap* (*"Backspace repeat haptic feedback"*).

---

## ⚡ Patch 2: Enter Key Tasker Event

- **How it works:** Hooks into Gboard's central input dispatch pipeline (`GoogleInputMethodService.dD`). Whenever the bottom-right key is pressed—whether for a standard newline (keycode 66/160) or an IME action (Send, Search, Go, Done, Next)—an Intent broadcast is immediately dispatched with zero latency.
- **Gboard Settings:** Adds a toggle under *Settings -> Preferences -> Key tap* (*"Send Enter key to Tasker"*).

### Tasker Setup Guide

1. Open **Tasker** -> go to the **Profiles** tab.
2. Tap **+** -> select **Event** -> **System** -> **Intent Received**.
3. In the **Action** field, enter:
   ```
   dev.custom.gboard.ENTER_PRESSED
   ```
4. Leave Cat, Scheme, Mime, and Path blank. Tap the back button to save the event.
5. Link a new Task. Inside the Task, you can use the following local variables:

| Tasker Variable | Description | Example Values |
| :--- | :--- | :--- |
| `%package` | Package name of the active foreground app | `com.whatsapp`, `com.google.android.apps.messaging` |
| `%action_type` | Action triggered | `ENTER`, `SEND`, `SEARCH`, `GO`, `DONE`, `NEXT` |
| `%key_code` | Raw keycode integer | `66` (Enter), `160` (Numpad Enter), `-10018` (Action) |
| `%text_before` | Text before the cursor (up to 100 chars) | `"Order #12345"` |
| `%timestamp` | Epoch time in milliseconds | `1726831000000` |

#### Example Tasker Actions:
- Flash a notification: `Enter pressed in %package: %action_type`
- Conditional action: Only run when pressed in WhatsApp:
  ```
  If %package ~ com.whatsapp
  ```

---

## 🛠️ Building Locally

```bash
# Clone the repository
git clone https://github.com/ausamnco/gboard-enc-patches.git
cd gboard-enc-patches

# Compile patches and generate release bundle (.mpp)
./build.sh
```

Compiled outputs will be located in `dist/`:
- `dist/patches-1.2.0.mpp`: Release bundle for Morphe Manager.
- `dist/gboard-backspace-haptics.jar`: Standard JAR format.
