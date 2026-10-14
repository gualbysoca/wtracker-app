package com.wtracker.field.services.location

import com.wtracker.field.configs.LocationConfig
import com.wtracker.field.services.models.GeoCoordinate
import com.wtracker.field.utils.GeoUtils

data class StopDetectorConfig(
    val radiusMeters: Double = LocationConfig.STOP_RADIUS_METERS,
    val stopAfterMillis: Long = LocationConfig.STOP_AFTER_MS,
    val inactivityAfterMillis: Long = LocationConfig.INACTIVITY_AFTER_MS,
    val extremeAbandonmentAfterMillis: Long = LocationConfig.EXTREME_ABANDONMENT_AFTER_MS,
)

data class LocationSample(val coordinate: GeoCoordinate, val atMillis: Long, val accuracyMeters: Float)

sealed interface StopDetectorState {
    data object Idle : StopDetectorState

    /** Moviéndose; `anchor` es el punto candidato a parada si el trabajador no se aleja. */
    data class Moving(val anchor: GeoCoordinate, val anchorSinceMillis: Long) : StopDetectorState

    data class Stopped(
        val anchor: GeoCoordinate,
        val startedAtMillis: Long,
        val isEscalatedToInactivity: Boolean,
        val isEscalatedToExtremeAbandonment: Boolean,
    ) : StopDetectorState
}

sealed interface StopTransition {
    data class Started(val coordinate: GeoCoordinate, val atMillis: Long) : StopTransition
    data class EscalatedToInactivity(val atMillis: Long) : StopTransition
    data class EscalatedToExtremeAbandonment(val atMillis: Long) : StopTransition
    data class Ended(val atMillis: Long) : StopTransition
}

data class StopEvaluation(val state: StopDetectorState, val transition: StopTransition?)

/**
 * Máquina de estados PURA (sin efectos): recibe el estado previo y una muestra GPS y
 * devuelve el nuevo estado + la transición a persistir. Facilita pruebas unitarias.
 */
object StopDetector {

    fun evaluate(state: StopDetectorState, sample: LocationSample, config: StopDetectorConfig): StopEvaluation =
        when (state) {
            StopDetectorState.Idle -> StopEvaluation(StopDetectorState.Moving(sample.coordinate, sample.atMillis), null)
            is StopDetectorState.Moving -> evaluateMoving(state, sample, config)
            is StopDetectorState.Stopped -> evaluateStopped(state, sample, config)
        }

    private fun evaluateMoving(
        state: StopDetectorState.Moving,
        sample: LocationSample,
        config: StopDetectorConfig,
    ): StopEvaluation {
        val effectiveRadius = config.radiusMeters + sample.accuracyMeters
        val leftAnchor = GeoUtils.distanceMeters(state.anchor, sample.coordinate) > effectiveRadius
        if (leftAnchor) return StopEvaluation(StopDetectorState.Moving(sample.coordinate, sample.atMillis), null)

        val stayedLongEnough = sample.atMillis - state.anchorSinceMillis >= config.stopAfterMillis
        if (!stayedLongEnough) return StopEvaluation(state, null)

        return StopEvaluation(
            state = StopDetectorState.Stopped(
                state.anchor, state.anchorSinceMillis,
                isEscalatedToInactivity = false,
                isEscalatedToExtremeAbandonment = false
            ),
            transition = StopTransition.Started(state.anchor, state.anchorSinceMillis),
        )
    }

    private fun evaluateStopped(
        state: StopDetectorState.Stopped,
        sample: LocationSample,
        config: StopDetectorConfig,
    ): StopEvaluation {
        val effectiveRadius = config.radiusMeters + sample.accuracyMeters
        val leftAnchor = GeoUtils.distanceMeters(state.anchor, sample.coordinate) > effectiveRadius
        if (leftAnchor) {
            return StopEvaluation(
                state = StopDetectorState.Moving(sample.coordinate, sample.atMillis),
                transition = StopTransition.Ended(sample.atMillis),
            )
        }

        val exceededExtreme = sample.atMillis - state.startedAtMillis >= config.extremeAbandonmentAfterMillis
        if (!state.isEscalatedToExtremeAbandonment && exceededExtreme) {
            return StopEvaluation(
                state = state.copy(isEscalatedToExtremeAbandonment = true, isEscalatedToInactivity = true),
                transition = StopTransition.EscalatedToExtremeAbandonment(sample.atMillis),
            )
        }

        val exceededInactivity = sample.atMillis - state.startedAtMillis >= config.inactivityAfterMillis
        if (state.isEscalatedToInactivity || !exceededInactivity) return StopEvaluation(state, null)

        return StopEvaluation(
            state = state.copy(isEscalatedToInactivity = true),
            transition = StopTransition.EscalatedToInactivity(sample.atMillis),
        )
    }
}
