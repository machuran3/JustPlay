#!/bin/bash
# Script de instalación de JustPlay POS para Linux (Debian, Ubuntu, Linux Mint, etc.)

set -e

echo "=========================================="
echo "    Instalador JustPlay POS para Linux    "
echo "=========================================="

# 1. Comprobar e instalar Java 21 si no existe
if ! command -v java &> /dev/null; then
    echo "[-] Java no encontrado. Instalando OpenJDK 21..."
    sudo apt update && sudo apt install -y openjdk-21-jre
else
    echo "[+] Java ya está instalado: $(java -version 2>&1 | head -n 1)"
fi

# 2. Directorio destino de la aplicación
APP_DIR="$HOME/.local/share/justplay"
mkdir -p "$APP_DIR"

# 3. Ubicar el archivo JAR
# Busca primero en la misma carpeta del script, luego en Descargas
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_SOURCE=""

if [ -f "$SCRIPT_DIR/JustPlayPOS.jar" ]; then
    JAR_SOURCE="$SCRIPT_DIR/JustPlayPOS.jar"
elif [ -f "$HOME/Descargas/JustPlayPOS.jar" ]; then
    JAR_SOURCE="$HOME/Descargas/JustPlayPOS.jar"
elif [ -f "$HOME/Downloads/JustPlayPOS.jar" ]; then
    JAR_SOURCE="$HOME/Downloads/JustPlayPOS.jar"
fi

if [ -n "$JAR_SOURCE" ]; then
    echo "[+] Copiando JustPlayPOS.jar desde $JAR_SOURCE..."
    cp "$JAR_SOURCE" "$APP_DIR/JustPlayPOS.jar"
    chmod +x "$APP_DIR/JustPlayPOS.jar"
else
    echo "[-] Descargando última versión de JustPlayPOS.jar desde GitHub..."
    curl -L -o "$APP_DIR/JustPlayPOS.jar" https://github.com/machuran3/JustPlay/releases/latest/download/JustPlayPOS.jar
    chmod +x "$APP_DIR/JustPlayPOS.jar"
fi

# 4. Icono de la aplicación
if [ -f "$SCRIPT_DIR/justplay_icon.png" ]; then
    cp "$SCRIPT_DIR/justplay_icon.png" "$APP_DIR/icon.png"
else
    # Descargar el icono desde GitHub si no está local
    curl -sL -o "$APP_DIR/icon.png" "https://raw.githubusercontent.com/machuran3/JustPlay/antigravity/justplay_icon.png" 2>/dev/null || true
fi

# 5. Crear el archivo .desktop para el Menú de Aplicaciones
APPS_DIR="$HOME/.local/share/applications"
mkdir -p "$APPS_DIR"

DESKTOP_FILE="$APPS_DIR/justplay-pos.desktop"

cat <<EOF > "$DESKTOP_FILE"
[Desktop Entry]
Version=1.0
Type=Application
Name=JustPlay POS
GenericName=Punto de Venta y Gestión de Salas
Comment=JustPlay POS Scanner & Gestión de Salas
Exec=java -Xmx2048m -jar "$APP_DIR/JustPlayPOS.jar"
Icon=$APP_DIR/icon.png
Terminal=false
Categories=Office;Finance;Utility;
StartupNotify=true
StartupWMClass=JustPlayPOS
EOF

chmod +x "$DESKTOP_FILE"

# 6. Copiar acceso directo al Escritorio si existe la carpeta Desktop/Escritorio
for DT in "$HOME/Desktop" "$HOME/Escritorio"; do
    if [ -d "$DT" ]; then
        cp "$DESKTOP_FILE" "$DT/JustPlayPOS.desktop"
        chmod +x "$DT/JustPlayPOS.desktop"
        if command -v gio &> /dev/null; then
            gio set "$DT/JustPlayPOS.desktop" metadata::trusted true 2>/dev/null || true
        fi
        echo "[+] Acceso directo creado en: $DT"
    fi
done

# 7. Actualizar base de datos de aplicaciones de GNOME
if command -v update-desktop-database &> /dev/null; then
    update-desktop-database "$APPS_DIR" 2>/dev/null || true
fi

echo "=========================================="
echo "  ¡Instalación completada con éxito!      "
echo "  Ya puedes presionar la tecla Super      "
echo "  y buscar 'JustPlay' para abrir el POS.  "
echo "=========================================="
