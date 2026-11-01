# Map Sender

Aplicación Android nativa para compartir ubicaciones desde Google Maps (u otras apps compatibles) y enviarlas a otro dispositivo Android (p. ej. estéreo del coche) a través de un servidor Gotify.

El receptor (app oficial Gotify Android) muestra una notificación. Al tocarla abre Waze con el destino, listo para navegar.

## Arquitectura

```
Teléfono (Map Sender) --POST /message JSON--> Gotify Server --> App Gotify Android (receptor) --tap--> Waze / navegador
```

- `app/src/main/.../domain/`: parsing de enlaces, validación, construcción de enlaces Waze y payload Gotify. Sin dependencias Android. Testeable.
- `app/src/main/.../data/`: `SettingsRepository` (DataStore), `GotifyClient` (OkHttp), sin lógica de UI.
- `app/src/main/.../ui/`: Compose + MVVM (`MainViewModel`, `SettingsViewModel`, `ShareViewModel`).
- Share: `MainActivity` con `intent-filter SEND text/plain` (Sharesheet).

Detalles: ver `docs/ARCHITECTURE.md`.

## Configuración (sin hardcodear)

Todo se configura en la app (pantalla Ajustes), persistido en DataStore:

- URL base Gotify (https), token de aplicación, nombre de dispositivo, prioridad (0-10),
  app de navegación preferida, parámetro `navigate`, confirmar antes de enviar,
  timeout, reintento ante fallo.

Ver `docs/CONFIG_EXAMPLE.md` (ejemplo sin credenciales).

## Uso

1. Ajustes: introduce URL y token, pulsa Probar conexión.
2. En Google Maps: Compartir → Map Sender.
3. Revisa destino → Enviar.
4. En el receptor: toca la notificación → se abre Waze.

Instalación y API: ver `docs/` y `README` completo abajo.

---

## Requisitos

- Android Studio Hedgehog o superior.
- JDK 17.
- `minSdk 26` / `targetSdk 34` / `compileSdk 34`.
- Servidor Gotify accesible por HTTPS + token de aplicación.
- En el receptor: app oficial Gotify Android + (opcional) Waze.

## Instalación (desarrollo)

1. Clona el proyecto (el remoto se proporcionará después; no hay workflows aún).
2. Abre la carpeta en Android Studio.
3. Deja que Gradle sincronice (`libs.versions.toml` fija las versiones).
4. Configura un emulador o dispositivo con API 26+.
5. Ejecuta `app` (Run). No hay firma/release configurada en esta etapa.

> Esta etapa es solo código fuente: no compilar APK/AAB aquí por restricción del proyecto.

## Configurar Gotify

1. En el servidor Gotify: Apps → Create application → copia el token (solo se muestra una vez).
2. En Map Sender → Ajustes:
   - URL: `https://gotify.tu-dominio.example` (sin `/` final).
   - Token: pega el token.
   - Dispositivo: p. ej. `movil-juan` (solo identificador local, se incluye en el título).
   - Prioridad: `5` por defecto.
   - Probar conexión → debe mostrar éxito (usa `GET /version`).

## Formato del mensaje Gotify

`POST {baseUrl}/message`, cabecera `X-Gotify-Key: <token>`, `Content-Type: application/json`:

```json
{
  "title": "Destino de movil-juan: Plaza Mayor",
  "message": "Plaza Mayor\n40.41536,-3.70737\n...",
  "priority": 5,
  "extras": {
    "client::notification": {
      "click": { "url": "https://waze.com/ul?ll=40.41536,-3.70737&navigate=yes" }
    }
  }
}
```

Solo se usa `client::notification.click.url`. No se usa `android::action.onReceive.intentUrl` porque abriría el destino automáticamente al recibirlo (no deseado).

## Pruebas unitarias

- `MapsLinkParserTest`, `NavigationLinkBuilderTest`, `GotifyPayloadBuilderTest`, `SettingsValidatorTest`.
- Ejecutar (cuando se habilite build): `./gradlew testDebugUnitTest`.

## Limitaciones conocidas

Ver `docs/LIMITATIONS.md`. Resumen:

- Los enlaces cortos (`maps.app.goo.gl`, `goo.gl/maps`) no contienen coordenadas; se resuelven siguiendo redirecciones HTTP (requiere Internet, timeout configurable). Si falla, se pide al usuario que comparta desde Maps con coordenadas o nombre.
- `geo:` y `lat,lng` sueltos sí se soportan; nombres sin coordenadas se envían con enlace de búsqueda (fallback documentado).
- Gotify Android abre el `click.url` en navegador si Waze no está instalado (comportamiento del receptor, no controlable desde el emisor).

## Seguridad

- Sin tokens en código, logs ni repo. El token vive solo en DataStore del dispositivo.
- Los logs redactan el token.
- Solo se aceptan destinos geográficos válidos (lat/lng en rango); nunca se ejecuta HTML/JS.
- Se advierte si la URL no es HTTPS.

## Estructura

```
app/src/main/java/com/mapsender/
  data/   SettingsRepository, GotifyClient, modelos
  domain/ GeoDestination, MapsLinkParser, NavigationLinkBuilder, GotifyPayloadBuilder, SettingsValidator, ShortLinkResolver
  ui/     MainActivity, MapSenderApp, ViewModels, screens/, theme/
app/src/test/...  pruebas unitarias
docs/ ARCHITECTURE.md API.md CONFIG_EXAMPLE.md LIMITATIONS.md GITHUB_ACTIONS_FUTURE.md
```

## Integración continua (GitHub Actions)

Workflow activo: `.github/workflows/android.yml` (push/PR a `main` y manual).

- Job `test`: `./gradlew testDebugUnitTest`.
- Job `build-debug`: `./gradlew assembleDebug` y sube el APK como artefacto `app-debug`.
- El APK se descarga en GitHub → Actions → run → Artifacts.
- Sin firma release (pendiente, cuando se pida).

## Licencia

MIT (ver `LICENSE` si se añade; por defecto todos los derechos reservados al autor hasta definir licencia).
