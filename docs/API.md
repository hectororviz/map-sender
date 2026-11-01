# API (Gotify)

Base URL configurable por el usuario, p. ej. `https://gotify.tu-dominio.example` (sin `/` final; la app lo normaliza).

## Probar conexión

```
GET {baseUrl}/version
Header: X-Gotify-Key: <app-token>
```

200 → OK. 401/403 → token inválido.

## Enviar mensaje

```
POST {baseUrl}/message
Headers:
  X-Gotify-Key: <app-token>
  Content-Type: application/json
Body:
{
  "title": "Plaza Mayor (de movil-x)",
  "message": "Plaza Mayor\n40.415363,-3.707346\n...",
  "priority": 5,
  "extras": {
    "client::notification": {
      "click": { "url": "https://waze.com/ul?ll=40.415363,-3.707346&navigate=yes" }
    }
  }
}
```

Notas:
- `title`, `message`, `priority` según API Gotify; solo `message` es obligatorio.
- `extras` solo se acepta con `application/json` (NO multipart).
- Prioridad 0–10 (ver https://gotify.net/docs/priority).
- Errores mapeados en `GotifyClient.mapHttpError`: 401/403 token, 404 URL base, 5xx servidor.
