# 🎮 JustPlay POS Scanner (Desktop)

Aplicación de escritorio moderna y ligera desarrollada en **Kotlin Multiplatform / Compose Desktop** para el ecosistema **JustPlay**. Diseñada para automatizar el registro de ventas de productos (snacks, bebidas, golosinas, accesorios) mediante pistolas lectoras de códigos de barras (USB o Bluetooth).

---

## ✨ Características Principales

- ⚡ **Detección Automática por Ráfaga de Teclado (HID)**: Compatible con cualquier pistola lectora estándar de códigos de barras (USB / Bluetooth) sin requerir controladores especiales ni tener el foco en un campo de texto específico.
- 💾 **Persistencia Local y Modo Offline (SQLite)**: Todas las ventas y el catálogo se guardan localmente en SQLite. Si se corta el internet, se puede seguir vendiendo normalmente.
- ☁️ **Sincronización en Vivo con Firebase**: Al concretar una venta, se envía de inmediato a `/registros` y `/auditoria_registros`, integrándose automáticamente con **AdminJustPlay** y **Employ_JustPlay**.
- 📦 **Control de Inventario y Alertas de Stock**: Decremento automático de stock al vender y alertas visuales cuando el producto está por agotarse.
- 🎨 **Interfaz Gaming Dark Theme**: Basada en Material 3 con paleta oscura (#0F111A, #1A1C29, #DEFF9A).
- ⌨️ **Atajo Rápido**: Pulsa `F12` en el teclado para abrir directamente el diálogo de cobro.

---

## 🏛️ Arquitectura SOLID

El proyecto sigue estrictamente los principios **SOLID**:
- **S (Single Responsibility)**: Separación estricta entre captura de hardware (`KeyboardBarcodeScanner`), base de datos local (`SqliteLocalStorage`), sincronizador en la nube (`FirebaseSyncService`), reglas de venta (`SaleService`) y estado visual (`PosViewModel`).
- **O (Open/Closed)**: Nuevos lectores de códigos o mecanismos de sincronización se agregan implementando `IBarcodeScanner` o `ICloudSyncService` sin modificar el código existente.
- **L (Liskov Substitution)**: Los repositorios e interfaces de almacenamiento son intercambiables.
- **I (Interface Segregation)**: Interfaces pequeñas y específicas (`IBarcodeScanner`, `IProductRepository`, `ILocalStorage`, `ISaleService`).
- **D (Dependency Inversion)**: `PosViewModel` y los servicios dependen de abstracciones, desacoplando la UI de la infraestructura.

---

## 🚀 Cómo Ejecutar y Compilar

### Requisitos:
- **JDK 17** o superior.

### 1. Ejecutar en modo desarrollo:
```bash
./gradlew run
```

### 2. Generar ejecutable / instalador para Windows (.msi / .exe):
```bash
./gradlew packageMsi
# o
./gradlew packageExe
```
El instalador generado se encontrará en `build/compose/binaries/main/msi/`.

### 3. Generar archivo JAR ejecutable universal:
```bash
./gradlew packageUberJarForCurrentOS
```
