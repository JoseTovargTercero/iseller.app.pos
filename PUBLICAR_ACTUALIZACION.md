# Publicar una actualización de iSeller POS

La app consulta `api/app_version.php` al abrir. Si el `version_code` publicado es mayor que el instalado,
avisa al usuario y se actualiza sola (descarga + instalador), sin salir de la app.

## Pasos

### 1. Subir la versión en `app/build.gradle`

```gradle
versionCode 4        // SIEMPRE mayor que el publicado
versionName "1.3"    // texto que ve el usuario
```

### 2. Compilar y probar

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
cd C:\iseller
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

### 3. Copiar el APK al hosting local

```powershell
Copy-Item app\build\outputs\apk\release\app-release.apk `
  C:\xampp\htdocs\iseller-tiendas.com\apk\iseller_pos.apk -Force
```

### 4. Editar `api/app_version.json`

```json
{
  "version_code": 4,
  "version_name": "1.3",
  "notas": "Qué cambió, en una línea.",
  "archivo": "apk/iseller_pos.apk"
}
```

### 5. Subir al hosting

Siempre estos dos archivos:

- `api/app_version.json`
- `apk/iseller_pos.apk`

La primera vez hay que subir también `api/app_version.php` (el endpoint que sirve la versión y el APK
con soporte de reanudación).

### 6. Comprobar antes de avisar

```powershell
curl.exe -H "X-Api-Key: <POS_API_KEY>" https://iseller-tiendas.com/inventario/api/app_version.php
(Get-FileHash -Algorithm SHA256 C:\xampp\htdocs\iseller-tiendas.com\apk\iseller_pos.apk).Hash.ToLower()
```

El JSON debe mostrar el `version_code` nuevo y un `sha256` igual al del APK que subiste.

## Reglas

- **`versionCode` siempre mayor.** Si no cambia, la app no detecta la actualización.
- **Misma firma siempre.** El APK se firma con `C:\iseller\keystore\iseller-pos.p12`
  (alias `iseller-pos`). Las credenciales están en `local.properties`, que **no** se versiona.
  Si se pierde o se cambia la keystore, Android rechaza las actualizaciones y hay que
  desinstalar/reinstalar en cada equipo.
- **Respalda la keystore y su contraseña** en un lugar seguro (fuera del repo).
- **Subir el APK completo.** Si queda truncado, el `sha256` no coincide y la app muestra
  "El archivo descargado no coincide con la versión publicada".
- **Equipos con versión anterior a la 1.2:** su descargador no reanuda; necesitan una instalación
  manual una sola vez.
- **Equipos con la 1.2 anterior (firmada en debug):** hay que desinstalarla e instalar la firmada
  en release una sola vez; después las actualizaciones funcionan normalmente.
