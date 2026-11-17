#!/bin/bash
set -e

# ==============================================================================
# Script de Publicación y Actualización Automática - JustPlay
# ==============================================================================
# Este script compila las 3 aplicaciones (Desktop POS, Admin y Empleado),
# prepara los archivos de lanzamiento e interactúa con GitHub para que los clientes
# reciban la actualización automáticamente sin pasar archivos manuales.
# ==============================================================================

VERSION="$1"
if [ -z "$VERSION" ]; then
    echo "=========================================================="
    echo "  JustPlay - Publicación de Actualizaciones (GitHub)"
    echo "=========================================================="
    read -p "Ingresa la versión a publicar (ejemplo: v1.0.1): " VERSION
fi

# Asegurar prefijo 'v'
if [[ ! "$VERSION" =~ ^v ]]; then
    VERSION="v$VERSION"
fi

CLEAN_VERSION="${VERSION#v}"

echo ""
echo "🚀 Iniciando proceso de release para la versión: $VERSION (v$CLEAN_VERSION)"
echo "----------------------------------------------------------"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# 1. Compilar Desktop POS (fatJar)
echo "📦 [1/3] Compilando JustPlay POS Desktop..."
cd "$ROOT_DIR/JustPlay_POS_Desktop"
./gradlew :fatJar --no-daemon
cd "$ROOT_DIR"

# 2. Compilar Apps Android (AdminJustPlay y Employ_JustPlay)
echo "📦 [2/3] Compilando AdminJustPlay y Employ_JustPlay..."
./gradlew :AdminJustPlay:assembleRelease :Employ_JustPlay:assembleRelease --no-daemon

# 3. Preparar directorio de distribución
echo "📁 [3/3] Empaquetando instaladores en build/release_assets/..."
DIST_DIR="$ROOT_DIR/build/release_assets"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# Copiar Desktop JAR
DESKTOP_SRC="$ROOT_DIR/JustPlay_POS_Desktop/build/libs/JustPlayPOS.jar"
if [ -f "$DESKTOP_SRC" ]; then
    cp "$DESKTOP_SRC" "$DIST_DIR/JustPlayPOS-$VERSION.jar"
    cp "$DESKTOP_SRC" "$DIST_DIR/JustPlayPOS.jar"
    echo "   ✔ Desktop: $DIST_DIR/JustPlayPOS-$VERSION.jar"
fi

# Copiar AdminJustPlay APK
ADMIN_APK="$ROOT_DIR/AdminJustPlay/app/build/outputs/apk/release/AdminJustPlay-release.apk"
if [ -f "$ADMIN_APK" ]; then
    cp "$ADMIN_APK" "$DIST_DIR/AdminJustPlay-$VERSION.apk"
    cp "$ADMIN_APK" "$DIST_DIR/AdminJustPlay.apk"
    echo "   ✔ Admin APK: $DIST_DIR/AdminJustPlay-$VERSION.apk"
fi

# Copiar Employ_JustPlay APK
EMPLOY_APK="$ROOT_DIR/Employ_JustPlay/app/build/outputs/apk/release/Employ_JustPlay-release.apk"
if [ -f "$EMPLOY_APK" ]; then
    cp "$EMPLOY_APK" "$DIST_DIR/Employ_JustPlay-$VERSION.apk"
    cp "$EMPLOY_APK" "$DIST_DIR/Employ_JustPlay.apk"
    echo "   ✔ Employ APK: $DIST_DIR/Employ_JustPlay-$VERSION.apk"
fi

echo ""
echo "=========================================================="
echo "  ✅ Compilación completada con éxito."
echo "=========================================================="
ls -lh "$DIST_DIR"
echo ""

# 4. Publicar en GitHub
if command -v gh &> /dev/null; then
    echo "🌐 GitHub CLI detectado. Publicando release automáticamente..."
    gh release create "$VERSION" \
        "$DIST_DIR/JustPlayPOS-$VERSION.jar" \
        "$DIST_DIR/AdminJustPlay-$VERSION.apk" \
        "$DIST_DIR/Employ_JustPlay-$VERSION.apk" \
        --title "JustPlay $VERSION" \
        --notes "Actualización automática JustPlay $VERSION: Mejoras de estabilidad, sincronización y correcciones en salas y productos."
    echo "🎉 ¡Release $VERSION publicado exitosamente en GitHub!"
else
    echo "ℹ️  GitHub CLI ('gh') no está configurado en tu terminal."
    echo "   Creando git tag local y subiéndolo al repositorio remoto..."
    
    # Crear tag si no existe
    if ! git rev-parse "$VERSION" >/dev/null 2>&1; then
        git tag -a "$VERSION" -m "Release $VERSION"
        echo "   ✔ Tag $VERSION creado."
    fi
    
    echo "   Intentando subir tag a origin..."
    if GIT_TERMINAL_PROMPT=0 git push origin "$VERSION" 2>/dev/null; then
        echo "   ✔ Tag $VERSION subido a GitHub (GitHub Actions compilará y adjuntará la release)."
    else
        echo "   ℹ️  Para publicar vía GitHub Actions, sube el tag manualmente:"
        echo "      git push origin $VERSION"
    fi

    echo ""
    echo "📌 También puedes subir los archivos compilados directamente por navegador:"
    echo "   1. Entra a: https://github.com/machuran3/JustPlay/releases/new?tag=$VERSION"
    echo "   2. Arrastra los 3 archivos desde la carpeta:"
    echo "      $DIST_DIR"
    echo "      • JustPlayPOS-$VERSION.jar (o JustPlayPOS.jar)"
    echo "      • AdminJustPlay-$VERSION.apk"
    echo "      • Employ_JustPlay-$VERSION.apk"
    echo "   3. Haz clic en 'Publish release'."
    echo ""
    echo "   ¡Listo! Cualquier app conectada detectará la nueva versión y se actualizará automáticamente."
fi
