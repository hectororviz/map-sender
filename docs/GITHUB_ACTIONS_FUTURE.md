# Integración con GitHub Actions (activa)

Workflow: `.github/workflows/android.yml`.

- Disparadores: `push` a `main`, `pull_request` a `main`, manual (`workflow_dispatch`).
- Job `test`: `./gradlew testDebugUnitTest` + sube `app/build/reports/tests/`.
- Job `build-debug` (requiere `test` en verde): `./gradlew assembleDebug` + sube el APK (`app-debug`).
- Runner `ubuntu-latest` (incluye Android SDK), JDK Temurin 17, caché Gradle.
- Descargar el APK: pestaña Actions → run → Artifacts → `app-debug`.

Fuera de alcance (cuando se pida): firma release (keystore + secrets `KEYSTORE_*`),
`assembleRelease`, publicación en Play/GitHub Releases.
