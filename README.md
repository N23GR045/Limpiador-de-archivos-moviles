# Espacio Libre — limpiador de almacenamiento para Android

App nativa (Kotlin + Jetpack Compose) que analiza el almacenamiento interno y propone qué borrar.

## Qué hace

| Categoría | Cómo se detecta | Selección por defecto |
|---|---|---|
| Duplicados | Tamaño → hash de los primeros 64 KB → MD5 completo | Todo salvo el más antiguo de cada grupo |
| Archivos grandes | ≥ 100 MB, o APK/XAPK/instaladores | Nada |
| Temporales y caché | `.tmp`, `.log`, `.part`, carpetas `cache`/`.thumbnails`, caché propia | Todo |
| Descargas antiguas | En `Download/` y sin modificar en 90 días | Nada |
| Fotos borrosas | Varianza del Laplaciano < 80 | Nada |
| Fotos similares | dHash de 64 bits, distancia de Hamming ≤ 8 | Todo salvo la más nítida |
| Caché de otras apps | `StorageStatsManager` + intent `ACTION_CLEAR_APP_CACHE` (Android 11+) | — |

Los umbrales están todos en `scanner/ScanConfig.kt`.

**Seguridad:** nada se borra sin confirmar; por defecto todo va a una papelera propia
(`.PapeleraDespeja`, recuperable 30 días). Si en un grupo de duplicados marcas todas las copias, el diálogo avisa.
Los hashes se guardan entre análisis, así que el segundo escaneo es mucho más rápido.

## Estructura

```
app/src/main/java/com/despeja/app/
├── MainActivity.kt          Punto de entrada
├── MainViewModel.kt         Estado de la app, navegación, escaneo y borrado
├── model/Models.kt          FileItem, Category, ScanResult…
├── scanner/
│   ├── ScanConfig.kt        Umbrales ajustables
│   ├── StorageScanner.kt    Recorrido de carpetas y clasificación
│   ├── DuplicateFinder.kt   Duplicados en 3 pasadas
│   ├── ImageAnalyzer.kt     Nitidez (Laplaciano) y similitud (dHash)
│   └── HashCache.kt         Caché de hashes entre escaneos
├── system/
│   ├── SystemAccess.kt      Permisos, espacio libre, caché de apps
│   └── TrashManager.kt      Papelera y borrado
└── ui/                      Pantallas Compose (inicio, escaneo, categoría, caché, papelera)
```

## Compilar

**Android Studio:** abre la carpeta del proyecto, espera a que sincronice Gradle y pulsa ▶ con un móvil Android conectado.

**Sin instalar nada (GitHub):** sube la carpeta a un repositorio de GitHub. El workflow
`.github/workflows/build-apk.yml` compila solo; descarga el APK en la pestaña *Actions* → última ejecución → *EspacioLibre-apk* (o en Releases).

Requisitos: Android 8.0 o superior.

## Permisos que pide la app

1. **Acceso a todos los archivos** (Android 11+): imprescindible para recorrer y borrar. Se activa en Ajustes al pulsar «Conceder acceso».
2. **Acceso a datos de uso** (opcional): solo para ver cuánto ocupa la caché de cada app.

> Google Play revisa a mano las apps que piden «Acceso a todos los archivos». Para uso personal (instalando el APK) no hay ningún problema.

## Limitaciones de Android

- `Android/data` y `Android/obb` están bloqueados desde Android 11: la caché interna de otras apps solo se puede vaciar con el diálogo del sistema (botón «Limpiar la caché de todas las apps»).
- Android no guarda de forma fiable la fecha de último acceso, así que «antigua» se basa en la fecha de modificación.
- Se analizan como máximo las 4000 fotos más recientes para que el escaneo no tarde demasiado.
