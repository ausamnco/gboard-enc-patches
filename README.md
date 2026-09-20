# Gboard Continuous Backspace Repeat Haptic Feedback Patch

<p align="center">
  <a href="https://morphe.software/add-source?github=USERNAME/gboard-backspace-haptics"><img alt="Add to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Source-00A8FF?style=for-the-badge"></a>
  <img alt="Gboard" src="https://img.shields.io/badge/Target-Gboard-4285F4?style=for-the-badge">
  <img alt="License" src="https://img.shields.io/badge/License-GPLv3-green?style=for-the-badge">
</p>

A standalone, Morphe/ReVanced-compatible bytecode patch repository for **Gboard** (`com.google.android.inputmethod.latin`). It introduces continuous tactile feedback pulses when the backspace key is held down during repeated character deletion.

---

## 📱 Quick Setup: Use in Morphe App

Once this repository is published on GitHub and released, you can install it directly from your Android phone using the **Morphe** app:

1. **Add Repository Source**:
   - Open **Morphe** on your phone.
   - Go to **Settings** ⚙️ -> **Sources**.
   - Tap **Add Source** and enter your repository URL:
     ```
     https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>
     ```
   - Alternatively, tap the **Add to Morphe** button above from your Android browser.

2. **Patch Gboard**:
   - In Morphe, navigate to the **Patcher** tab.
   - Select **Gboard** (from your installed apps or a downloaded APK file).
   - In the patch selection list, you will see **Backspace Repeat Haptic Feedback** alongside any patches from other enabled sources (such as `jasonwu1994/Gboard-patches`).
   - Select the patches you want and tap **Patch**!
   - Install the generated APK.

3. **Enable Haptic Feedback in Gboard**:
   - Ensure Android's system haptics toggle is enabled (*Settings -> Sound & vibration -> Haptics*).
   - Open Gboard Settings -> **Preferences**.
   - Ensure **Haptic feedback on keypress** is turned ON.
   - Adjust **Vibration strength on keypress** to your preference (e.g. 5–15 ms).
   - When holding backspace, feel a crisp tactile pulse for every single character deleted!

---

## 🚀 Publishing This Repository to GitHub

Follow these simple steps to push this repository to your GitHub account and generate your first Morphe release:

### 1. Create a New Repository on GitHub
- Go to [github.com/new](https://github.com/new).
- Name your repository (e.g., `gboard-backspace-haptics` or `gboard-haptics-patch`).
- Set it to **Public** (required so the Morphe app can fetch releases without authentication).
- Do **not** initialize with a README, .gitignore, or license (they are already included here).

### 2. Push Your Local Code
In this directory, run:
```bash
# Initialize git and stage all files
git init -b main
git add .
git commit -m "feat: initial release of continuous backspace repeat haptics patch"

# Link your GitHub repository
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>.git

# Push the main branch
git push -u origin main

# Tag and push version 1.0.0 (this automatically triggers the GitHub Actions release workflow!)
git tag v1.0.0
git push origin v1.0.0
```

### 3. Automated Release Creation
- The included [GitHub Actions workflow](.github/workflows/release.yml) will trigger automatically upon pushing the `v1.0.0` tag.
- It will compile the Kotlin bytecode, package `patches-1.0.0.mpp`, generate the metadata manifests, and publish a GitHub Release with the patch bundle attached.
- As soon as the release completes (usually ~1–2 minutes), your Morphe app will be able to discover and install it!

---

## 🛠️ Local Development & Manual Build

If you want to build or test the patch package locally on your computer:

```bash
# Run the self-contained build script
chmod +x build.sh
./build.sh
```

Outputs produced:
- `dist/patches-1.0.0.mpp`: Standard Morphe release asset.
- `dist/gboard-backspace-haptics.jar`: Local convenience JAR.

To test applying the patch to a local Gboard APK via CLI:
```bash
java -jar libs/morphe-cli.jar patch \
    -p dist/patches-1.0.0.mpp \
    -o gboard-patched.apk \
    -f "path/to/gboard.apk"
```

---

## 🔍 How the Patch Works

### 1. The Problem in Stock Gboard
Stock Gboard only performs haptic feedback when a key is initially pressed (`ACTION_DOWN`). When a key auto-repeats (such as holding down backspace to delete multiple characters), Gboard's timer runnable invokes repeat event dispatches, but skips tactile feedback.

### 2. Bytecode Hook Point
This patch uses Morphe to inject a hook into Gboard's `PointerTracker.q` repeat dispatch method (`Lpvi;->q`):
```java
// Method signature:
// q(ActionDef actionDef, SoftKeyDef softKeyDef, long eventTime, boolean isRepeat, long uptime, int count)

// Injected at opcode index 0:
this.morpheBackspaceRepeatHaptic(actionDef);
```

### 3. Injected Helper Method
The injected helper method:
1. Validates that `actionDef` and its `KeyData` (`actionDef.b()`) are non-null.
2. Checks that the keycode is `KeyEvent.KEYCODE_DEL` (`67` / `0x43`).
3. Resolves the active `SoftKeyView` (`this.m`).
4. Invokes Gboard's native `PressEffectPlayer` module:
   ```java
   phk.a().d(softKeyView, 0); // PerformBasicTapEffect
   ```
   This automatically respects system vibration toggles, Gboard's user preference, and the custom vibration duration slider!
5. Catches any runtime exceptions and safely falls back to Android's `view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`.

### 4. Obfuscation Resilience & Compatibility
- Obfuscated class names (`phk`, `phm`) and field names (`m`) are **dynamically resolved** by analyzing method instruction flows at patch time, rather than hardcoded.
- Compatible with all existing patch suites (e.g. `jasonwu1994/Gboard-patches`), as this patch operates on `PointerTracker.q`, whereas other suites target pointer ownership methods (`B`, `pointerCancel`, `pointerReset`).

---

## 📄 License
Released under the [GNU General Public License v3.0](LICENSE).
