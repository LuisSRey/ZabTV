# ZabTV

Reproductor de listas IPTV para Android TV / Fire TV y móvil.

**La app no incluye, aloja ni distribuye ningún contenido.** El usuario aporta
sus propias listas (M3U) o cuentas (Xtream). Sin lista propia, la app no muestra nada.

## Requisitos

- Android Studio (AGP 9.5.0) o Gradle wrapper incluido
- `minSdk 23`, `target/compileSdk` según `app/build.gradle.kts`

## Compilar y probar

```powershell
# Suite completa de tests unitarios (Robolectric)
.\gradlew.bat :app:testDebugUnitTest --console=plain

# APK debug
.\gradlew.bat :app:assembleDebug --console=plain
```

> Tras cambiar dependencias, añade `--no-configuration-cache` (bug intermitente
> en `dataBindingMergeDependencyArtifactsDebug`).

## Licencia

Copyright (c) 2026 Luis Rey González. Todos los derechos reservados.
Ver `LICENSE`: código visible solo a efectos de transparencia; prohibida la
copia, redistribución o modificación sin autorización escrita.
