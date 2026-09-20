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
if [ -f "/home/ausamnco/.local/kotlinc/lib/kotlin-compiler.jar" ]; then
    KOTLINC_JAR="/home/ausamnco/.local/kotlinc/lib/kotlin-compiler.jar"
elif [ -f "${HOME}/.local/kotlinc/lib/kotlin-compiler.jar" ]; then
    KOTLINC_JAR="${HOME}/.local/kotlinc/lib/kotlin-compiler.jar"
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
        echo "Downloading morphe-cli.jar..."
        curl -fSL -o "${MORPHE_JAR}" "https://github.com/MorpheApp/morphe-cli/releases/download/v1.16.0/morphe-cli.jar" || \
        curl -fSL -o "${MORPHE_JAR}" "https://github.com/MorpheApp/morphe-cli/releases/latest/download/morphe-cli.jar"
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
    "${SCRIPT_DIR}/src/main/kotlin/dev/custom/gboardpatches/patches/haptics/BackspaceRepeatHapticsPatch.kt"

# 6. Create Manifest
MANIFEST_FILE="${BUILD_DIR}/MANIFEST.MF"
cat << EOF > "${MANIFEST_FILE}"
Manifest-Version: 1.0
Version: ${VERSION}
Created-By: Morphe Patch Builder
EOF

# 7. Package .mpp and .jar
JAR_BIN="${JAVA//java/jar}"
MPP_OUTPUT="${DIST_DIR}/patches-${VERSION}.mpp"
JAR_OUTPUT="${DIST_DIR}/gboard-backspace-haptics.jar"

echo "Packaging ${MPP_OUTPUT}..."
"${JAR_BIN}" -cfm "${MPP_OUTPUT}" "${MANIFEST_FILE}" -C "${BUILD_DIR}" .
cp "${MPP_OUTPUT}" "${JAR_OUTPUT}"

# 8. Verify using Morphe
echo "Validating patch package with Morphe CLI..."
"${JAVA}" -jar "${MORPHE_JAR}" list-patches --patches="${MPP_OUTPUT}"

echo "=== Build Complete ==="
echo "Morphe Release Asset : ${MPP_OUTPUT}"
echo "Local Convenience JAR : ${JAR_OUTPUT}"
