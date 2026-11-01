package com.mapsender.domain

/** Valida la configuración introducida por el usuario (nunca hay valores hardcodeados). */
object SettingsValidator {

    data class Validation(val errors: Map<String, String>) {
        val isValid: Boolean get() = errors.isEmpty()
        fun errorFor(field: String): String? = errors[field]
    }

    fun validate(
        baseUrl: String,
        token: String,
        deviceName: String,
        priority: Int,
        timeoutSeconds: Int
    ): Validation {
        val errors = mutableMapOf<String, String>()
        val url = baseUrl.trim().trimEnd('/')
        if (url.isBlank()) errors["baseUrl"] = "La URL es obligatoria"
        else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                errors["baseUrl"] = "Debe empezar por http:// o https://"
            } else if (!url.matches(Regex("""https?://[^/\s]+.*"""))) {
                errors["baseUrl"] = "URL no válida"
            }
        }
        if (token.trim().isBlank()) errors["token"] = "El token es obligatorio"
        else if (token.trim().length < 8) errors["token"] = "El token parece demasiado corto"
        if (deviceName.trim().isBlank()) errors["deviceName"] = "El nombre es obligatorio"
        else if (deviceName.trim().length > 60) errors["deviceName"] = "Máximo 60 caracteres"
        if (priority !in 0..10) errors["priority"] = "Prioridad entre 0 y 10"
        if (timeoutSeconds !in 3..120) errors["timeout"] = "Timeout entre 3 y 120 s"
        return Validation(errors)
    }

    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

    fun isInsecureHttp(url: String): Boolean =
        url.trim().lowercase().startsWith("http://")
}
