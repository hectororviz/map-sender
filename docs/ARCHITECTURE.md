# Arquitectura

## Diagrama

```
[Google Maps / app] --SEND text/plain--> [Map Sender]
    MapsLinkParser (domain, puro Kotlin)
    ShortLinkResolver (OkHttp HEAD/GET, sigue Location, máx 5 saltos)
    NavigationLinkBuilder (Waze universal / Maps / geo)
    GotifyPayloadBuilder (JSON + extras)
        --POST {baseUrl}/message (application/json, X-Gotify-Key)-->
[Gotify Server] --push--> [Gotify Android oficial en estéreo]
    --tap en notificación (client::notification.click.url)--> [Waze / navegador]
```

## Capas (MVVM)

- `domain/`: `GeoDestination`, `MapsLinkParser`, `NavigationLinkBuilder`,
  `GotifyPayloadBuilder`, `SettingsValidator`, `ShortLinkResolver`. Sin Android.
- `data/`: `AppSettings`, `SettingsRepository` (DataStore Preferences),
  `GotifyClient` (OkHttp, timeouts configurables, errores mapeados a mensajes).
- `ui/`: `MainActivity` (single-activity, Navigation Compose), `MainViewModel`,
  `SettingsViewModel`, `ShareViewModel`, `screens/*`, `theme/*`.

## Decisiones

- Sin Hilt/Dagger: dependencias manuales en `MapSenderApp` (app liviana).
- Serialización: `kotlinx.serialization.json` para construir el payload (evita Moshi/Gson).
- HTTP: OkHttp 4 (mantenido, sin Retrofit para un solo endpoint; `GET /version` test + `POST /message`).
- DataStore Preferences para ajustes (persisten entre reinicios/actualizaciones).
- Navigation Compose para `main → settings → share`.
- `minSdk 26`: DataStore, Compose y OkHttp lo exigen razonablemente; `target/compile 34`.

## Extras Gotify (verificado en docs oficiales)

- Solo `client::notification.click.url` con `Content-Type: application/json`.
- Verificado: https://gotify.net/docs/msgextras — `click.url` soportado en gotify/android ≥ 2.0.10.
- No se usa `android::action.onReceive.intentUrl` porque abriría el destino al recibir
  (viola el requisito "no navegar automáticamente").
