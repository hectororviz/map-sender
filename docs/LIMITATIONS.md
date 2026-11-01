# Limitaciones conocidas

1. **Enlaces cortos** (`maps.app.goo.gl/…`, `goo.gl/maps/…`): Google ya no redirige
   (devuelve HTTP 200 con la página del lugar, sin coordenadas en el HTML).
   Cadena de resolución: UA móvil (puede devolver el 302 clásico) → pin `!3d/!4d`
   → `q/ll` → `/@` → WebView oculto 25 s (como el navegador; requiere System WebView)
   → **geocodificación Nominatim** (nombre completo y, si no hay resultados,
   reintento sin el nombre del local) → **búsqueda Waze** (`q=`).
   Pantalla "Probar resolución" (en Ajustes) ejecuta la cadena en dry-run con traza.
2. **Nombre sin coordenadas** (`?q=Plaza+Mayor` sin lat/lng): no es enviable como Waze;
   se informa como "no reconocido". Workaround documentado: en Maps, usar las coordenadas
   o el pin exacto antes de compartir.
3. **Gotify Android abre `click.url` en el navegador si Waze no está instalado.**
   Es comportamiento del receptor oficial, no controlable desde el emisor. Recomendación:
   instalar Waze en el estéreo o elegir "BROWSER/GOOGLE_MAPS" en ajustes.
4. **`android::action.onReceive.intentUrl` no se usa**: abriría el destino automáticamente
   al recibir el push (verificado en docs; descartado por requisito).
5. **Token en DataStore Preferences (no cifrado)**: DataStore no cifra por defecto.
   Mejora futura: `EncryptedFile`/EncryptedPreferences o Keystore. No se registra en logs.
6. **HTTP sin cifrar**: permitido para pruebas en LAN pero la app avisa; usar HTTPS en producción.
7. **Sharesheet**: depende del OEM; algunos estéreos limitan apps en segundo plano — mantener
   Gotify Android excluida de optimización de batería.
