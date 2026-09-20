#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="${SCRIPT_DIR}/dist"
BUILD_DIR="${SCRIPT_DIR}/build/classes"
LIBS_DIR="${SCRIPT_DIR}/libs"

echo "=== Building Gboard Backspace Continuous Haptics Patch ==="

# 1. Resolve Version from gradle.properties
VERSION="$(grep -m1 '^version=' "${SCRIPT_DIR}/gradle.properties" | cut -d'=' -f2 | tr -d ' ')"
echo "Target Patch Version: ${VERSION}"

# 2. Resolve Java
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    JAVA="${JAVA_HOME}/bin/java"
elif [ -x "/home/ausamnco/.local/jdk-21/bin/java" ]; then
    JAVA="/home/ausamnco/.local/jdk-21/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA="$(command -v java)"
else
    echo "Error: Java 21+ is required but not found." >&2
    exit 1
fi
echo "Using Java: $(${JAVA} -version 2>&1 | head -n 1)"

# 3. Resolve Kotlin compiler
if [ -f "${HOME}/.local/kotlinc/lib/kotlin-compiler.jar" ]; then
    KOTLINC_JAR="${HOME}/.local/kotlinc/lib/kotlin-compiler.jar"
elif [ -f "/home/ausamnco/.local/kotlinc/lib/kotlin-compiler.jar" ]; then
    KOTLINC_JAR="/home/ausamnco/.local/kotlinc/lib/kotlin-compiler.jar"
elif [ -f "${LIBS_DIR}/kotlin-compiler.jar" ]; then
    KOTLINC_JAR="${LIBS_DIR}/kotlin-compiler.jar"
elif command -v kotlinc >/dev/null 2>&1; then
    KOTLINC_BIN="$(command -v kotlinc)"
    KOTLINC_DIR="$(dirname "$(dirname "$(readlink -f "${KOTLINC_BIN}")")")"
    KOTLINC_JAR="${KOTLINC_DIR}/lib/kotlin-compiler.jar"
else
    echo "Error: kotlin-compiler.jar not found." >&2
    exit 1
fi
echo "Using Kotlin Compiler: ${KOTLINC_JAR}"

# 4. Resolve Morphe CLI / Patcher Framework
mkdir -p "${LIBS_DIR}"
MORPHE_JAR="${LIBS_DIR}/morphe-cli.jar"
if [ ! -f "${MORPHE_JAR}" ]; then
    if [ -f "/home/ausamnco/.local/morphe/morphe-cli.jar" ]; then
        cp "/home/ausamnco/.local/morphe/morphe-cli.jar" "${MORPHE_JAR}"
    elif [ -f "${HOME}/.local/morphe/morphe-cli.jar" ]; then
        cp "${HOME}/.local/morphe/morphe-cli.jar" "${MORPHE_JAR}"
    else
        echo "Downloading morphe-desktop jar..."
        curl -fSL -o "${MORPHE_JAR}" "https://github.com/MorpheApp/morphe-desktop/releases/download/v1.16.0/morphe-desktop-1.16.0-all.jar"
    fi
fi
echo "Using Morphe Framework: ${MORPHE_JAR}"

# Prepare folders
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}"
mkdir -p "${DIST_DIR}"

# 5. Compile Kotlin sources
echo "Compiling Kotlin sources..."
"${JAVA}" -jar "${KOTLINC_JAR}" \
    -cp "${MORPHE_JAR}" \
    -d "${BUILD_DIR}" \
    -Xskip-metadata-version-check \
    -Xcontext-receivers \
    "${SCRIPT_DIR}/src/main/kotlin/dev/custom/gboardpatches/patches/haptics/RepeatKeyActionFingerprint.kt" \
    "${SCRIPT_DIR}/src/main/kotlin/dev/custom/gboardpatches/patches/haptics/PressEffectPlayerFinder.kt" \
    "${SCRIPT_DIR}/src/main/kotlin/dev/custom/gboardpatches/patches/haptics/BackspaceRepeatHapticsSettingsPatch.kt" \
    "${SCRIPT_DIR}/src/main/kotlin/dev/custom/gboardpatches/patches/haptics/BackspaceRepeatHapticsPatch.kt"

# 6. Create Manifest
MANIFEST_FILE="${BUILD_DIR}/MANIFEST.MF"
TIMESTAMP="$(date +%s000)"
cat << EOF > "${MANIFEST_FILE}"
Manifest-Version: 1.0
Name: Gboard Backspace Haptics
Description: Continuous tactile haptic feedback during Gboard backspace repeat deletion.
Version: ${VERSION}
Timestamp: ${TIMESTAMP}
Source: https://github.com/ausamnco/gboard-enc-patches
Author: ausamnco
Contact: https://github.com/ausamnco/gboard-enc-patches/issues
Website: https://github.com/ausamnco/gboard-enc-patches
License: GPLv3
Patcher-Version: 1.8.0
EOF

# 7. Package temporary jar for D8 dexing
JAR_BIN="${JAVA//java/jar}"
R8_JAR="${LIBS_DIR}/r8.jar"
if [ ! -f "${R8_JAR}" ]; then
    echo "Downloading D8 compiler (r8)..."
    curl -fSL -o "${R8_JAR}" "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.2.42/r8-8.2.42.jar"
fi

TEMP_CLASSES_JAR="${BUILD_DIR}/classes-temp.jar"
"${JAR_BIN}" -cfm "${TEMP_CLASSES_JAR}" "${MANIFEST_FILE}" -C "${BUILD_DIR}" dev

# 8. Run D8 to generate classes.dex for Android ART/Dalvik runtime
echo "Compiling Dalvik executable (classes.dex) with D8..."
DEX_DIR="${BUILD_DIR}/dex_output"
rm -rf "${DEX_DIR}"
mkdir -p "${DEX_DIR}"

"${JAVA}" -cp "${R8_JAR}" com.android.tools.r8.D8 \
    --release \
    --min-api 26 \
    --output "${DEX_DIR}" \
    "${TEMP_CLASSES_JAR}" \
    --classpath "${MORPHE_JAR}"

cp "${DEX_DIR}/classes.dex" "${BUILD_DIR}/classes.dex"
rm -f "${TEMP_CLASSES_JAR}"
rm -rf "${DEX_DIR}"

# 9. Package final .mpp and .jar
MPP_OUTPUT="${DIST_DIR}/patches-${VERSION}.mpp"
JAR_OUTPUT="${DIST_DIR}/gboard-backspace-haptics.jar"

echo "Packaging final patch bundle ${MPP_OUTPUT}..."
rm -f "${MPP_OUTPUT}" "${JAR_OUTPUT}"
"${JAR_BIN}" -cfm "${MPP_OUTPUT}" "${MANIFEST_FILE}" -C "${BUILD_DIR}" dev -C "${BUILD_DIR}" classes.dex
if [ -d "${BUILD_DIR}/META-INF" ]; then
    "${JAR_BIN}" -uf "${MPP_OUTPUT}" -C "${BUILD_DIR}" META-INF
fi
cp "${MPP_OUTPUT}" "${JAR_OUTPUT}"

# 10. Verify using Morphe CLI
echo "Validating patch package with Morphe CLI..."
"${JAVA}" -jar "${MORPHE_JAR}" list-patches --patches="${MPP_OUTPUT}"

echo "=== Build Complete ==="
echo "Morphe Release Asset : ${MPP_OUTPUT}"
echo "Local Convenience JAR : ${JAR_OUTPUT}"
