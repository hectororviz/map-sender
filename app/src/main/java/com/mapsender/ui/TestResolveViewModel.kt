package com.mapsender.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapsender.MapSenderApp
import com.mapsender.domain.GotifyPayloadBuilder
import com.mapsender.domain.NavigationLinkBuilder
import com.mapsender.domain.OutgoingPlace
import com.mapsender.domain.ResolvePlaceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Dry-run de resolución: ejecuta la misma cadena sin enviar nada a Gotify. */
class TestResolveViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MapSenderApp).settingsRepository

    data class StepUi(val name: String, val ok: Boolean, val detail: String, val ms: Long)
    data class TestUi(
        val running: Boolean = false,
        val steps: List<StepUi> = emptyList(),
        val result: String? = null
    )

    private val _ui = MutableStateFlow(TestUi())
    val ui: StateFlow<TestUi> = _ui.asStateFlow()

    fun runTest(text: String?, context: Context) {
        if (text.isNullOrBlank()) {
            _ui.value = TestUi(result = "Pega primero un enlace o texto compartido.")
            return
        }
        _ui.value = TestUi(running = true)
        viewModelScope.launch {
            try {
                val settings = repo.settings.first()
                val useCase = ResolvePlaceUseCase()
                val acc = mutableListOf<StepUi>()
                val outcome = useCase.resolve(text, context) { event ->
                    if (event is ResolvePlaceUseCase.Event.TraceStep) {
                        acc.add(StepUi(event.step.name, event.step.ok, event.step.detail, event.step.ms))
                        _ui.value = TestUi(running = true, steps = acc.toList())
                    }
                }
                _ui.value = TestUi(steps = acc.toList(), result = preview(outcome, settings.deviceName.ifBlank { "prueba" }, settings.priority, settings.navApp.name))
            } catch (e: Exception) {
                _ui.value = TestUi(result = "Error inesperado: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun preview(
        outcome: ResolvePlaceUseCase.Outcome,
        device: String,
        priority: Int,
        navApp: String
    ): String = when (outcome) {
        is ResolvePlaceUseCase.Outcome.Located -> {
            val place = OutgoingPlace.Located(outcome.destination, "(navegación según ajustes)")
            val p = GotifyPayloadBuilder.build(place, device, priority)
            "SE ENVIARÍA (vía ${outcome.via}):\nTítulo: ${p.title}\nMensaje: ${p.message}\nClick: ${p.clickUrl}"
        }
        is ResolvePlaceUseCase.Outcome.Named -> {
            val place = OutgoingPlace.Search(outcome.name, "(búsqueda según ajustes)")
            val p = GotifyPayloadBuilder.build(place, device, priority)
            "SE ENVIARÍA (búsqueda, sin coords):\nTítulo: ${p.title}\nMensaje: ${p.message}"
        }
        is ResolvePlaceUseCase.Outcome.Unrecognized ->
            "SIN RESULTADO: ${outcome.reason}"
        is ResolvePlaceUseCase.Outcome.Failed ->
            "FALLO: ${outcome.message}"
    }.let { "$it\n\nApp navegación: $navApp" }

    fun clear() {
        _ui.value = TestUi()
    }
}
