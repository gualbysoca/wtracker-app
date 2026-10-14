# WTracker Campo (Android)

App nativa en Kotlin para personal de campo (reponedores, vendedores, cobradores y repartidores). Sirve para:
- registrar visitas;
- capturar evidencias fotográficas verificadas (GPS + hora sellados);
- controlar la jornada.

Funciona **offline-first** y sincroniza con el Backoffice (`wtracker-web/backend`).

## Stack
- Kotlin 2.1 · Jetpack Compose (Material 3) · Navigation Compose
- Room (persistencia local) · WorkManager (sincronización)
- Retrofit + OkHttp + kotlinx.serialization (API REST con JWT y refresh automático)
- CameraX (cámara propia) · Fused Location Provider · osmdroid (mapa sin API key)
- minSdk 26 · target/compile 35 · Java 17

## Arquitectura (`app/src/main/java/com/wtracker/field`)
| Carpeta | Responsabilidad |
|---|---|
| `configs/` | Constantes de negocio, Room, red, mapa y contenedor DI manual (`AppContainer`). |
| `controllers/` | ViewModels: estado inmutable (`StateFlow`) y eventos de un solo uso. |
| `middlewares/` | Interceptores HTTP: header `Authorization` y renovación de token ante un 401. |
| `routes/` | `AppNavGraph`: la sesión decide entre login y el área autenticada. |
| `services/` | Lógica de negocio y acceso a datos. Subcarpetas: `api`, `local`, `auth`, `clients`, `visits`, `sync`, `camera`, `location`, `shift`, `connectivity`. |
| `utils/` | Utilidades puras: `AppResult`, tiempo, geo, ruta, hashing y mapeo de errores. |
| `validations/` | Reglas de formulario, de visita y de calidad del GPS. |
| `ui/` | `theme/` (tokens de diseño), `components/` (atómicos) y `screens/` (UI sin lógica + wrapper de ruta). |

## Flujo principal
1. **Login**: `POST /auth/login`. La sesión se guarda en DataStore.
2. **Hoja de ruta**: `GET /mobile/clients`. Los clientes se guardan en Room y se ordenan por vecino más cercano desde tu posición. Se muestran en mapa y en lista.
3. **Jornada**: un servicio en primer plano (`ShiftTrackingService`) registra puntos GPS y detecta **paradas** (más de 5 min en 60 m) e **inactividad** (más de 20 min).
4. **Visita**: el estado puede ser `en_ruta`, `visitado` o `fallido`. Los dos últimos **exigen foto**.
5. **Cámara**: sólo CameraX dentro de la app. No hay galería, edición ni filtros. Al disparar se lee el GPS en paralelo y se sellan sobre la imagen y en EXIF las coordenadas, la precisión y la hora. Además se calcula un SHA-256 de la foto. Se rechazan las ubicaciones simuladas y las de precisión peor que 75 m.
6. **Sincronización**: los registros se guardan primero en Room. WorkManager envía `POST /mobile/visits` y luego `POST /mobile/visits/:id/evidence` (multipart), manteniendo el orden cronológico.
   - Errores de red, 5xx o 401: se reintenta.
   - Errores 4xx: el registro queda en `REJECTED`.

## Configuración
- URL de la API en `gradle.properties` → `wtracker.apiBaseUrl` (por defecto `http://10.0.2.2:3001/api/v1/` para el emulador).
- `debug` permite HTTP en claro (`src/debug/res/xml/network_security_config.xml`); `release` sólo HTTPS.
- Abrir en Android Studio (Ladybug o superior) con JDK 17. Para generar el wrapper: `gradle wrapper`.

## Pendientes del backend
- `auth.service.loginUser` sólo admite `admin` y `supervisor`. **Hay que permitir los roles de campo** para que la app pueda autenticarse.
- La ruta real es `/mobile/clients` (no `/mobile/clientes`).
- El endpoint de evidencia ignora la parte `metadata` del multipart, que incluye precisión, SHA-256, mock y dispositivo. Se recomienda guardarla en `evidence.metadata` (JSONB).
## Publicar una nueva versión (autoactualización)

La app no está en la tienda: detecta versiones nuevas leyendo `releases/version.json` del repo (rama `main`).

1. Sube `versionCode` (y `versionName`) en `app/build.gradle.kts`.
2. Ejecuta `scripts/publish-release.sh "Notas de la versión"`. Compila el release firmado y genera `releases/wtracker-<versión>.apk` + `releases/version.json`.
3. Haz commit y push de la carpeta `releases/`.

Las apps instaladas con un `versionCode` menor mostrarán el botón **Actualizar** en la pantalla de bienvenida y en la hoja de ruta.

**Firma:** todas las versiones deben firmarse con la misma keystore (`~/.wtracker/wtracker-release.jks`, credenciales en `keystore.properties`, ignorado por git). Respáldala: si se pierde, las apps instaladas no podrán actualizarse. Al pasar de una build debug a la primera release firmada hay que desinstalar la app una sola vez.
