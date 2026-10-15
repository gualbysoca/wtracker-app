package com.wtracker.field.utils

import com.wtracker.field.services.models.FieldClient
import com.wtracker.field.services.models.VisitStatus
import com.wtracker.field.services.models.BusinessSettings

enum class GamificationBadge {
    ON_FIRE, // 3+ visitas exitosas seguidas
    SNIPER, // 100% efectividad (min 3)
    SNAIL, // Mucha inactividad
    UNLUCKY, // 2+ fallidas seguidas
}

data class GamificationStatus(
    val badges: List<GamificationBadge>,
    val latestMessage: String,
)

object GamificationEngine {

    private val successRachaMessages = listOf(
        "¡Estás ON FIRE! Ni Usain Bolt te alcanza. 🔥",
        "¡Máquina total! Vas volando. 🚀",
        "Otra más pal bote. Tu jefe debe estar llorando de alegría. 🤑",
        "¡Implacable! Eres el Terminator de las ventas. 🤖",
    )

    private val successNormalMessages = listOf(
        "¡Excelente! Otra víctima de tu éxito. 😎",
        "¡Una menos! A este paso terminas antes de almorzar. 🍔",
        "Pim, pam, pum. Trabajo hecho. 🎯",
        "¡Bien ahí! Sigue así que hoy coronas. 👑",
    )

    private val failureMessages = listOf(
        "¡Oww! Qué pena que no pudiste completar esta tarea, a la siguiente te irá mejor. 🩹",
        "Misión fallida. Presiona F para mostrar respetos. 💀",
        "Otro cliente que nos rompe el corazón. 💔",
        "Ni modo, no se puede ganar siempre... ¡A por el próximo! 🏃‍♂️",
    )

    private val snailMessages = listOf(
        "¿Te quedaste dormido? 🐢 ¡A moverse!",
        "Mucho Netflix y poco trabajo... 👀",
        "¿Estás tomando un cafecito? ¡Vuelve al ruedo! ☕",
    )

    fun evaluate(clients: List<FieldClient>, nowMillis: Long, settings: BusinessSettings): GamificationStatus {
        if (!settings.gamificationEnabled) {
            return GamificationStatus(emptyList(), "¡A darle con todo!")
        }

        val completed = clients.filter { it.currentStatus.isFinal }.sortedByDescending { it.statusUpdatedAtMillis ?: 0L }
        
        if (completed.isEmpty()) {
            return GamificationStatus(emptyList(), "¡A darle con todo!")
        }

        val lastCompleted = completed.first()
        val isSuccess = lastCompleted.currentStatus == VisitStatus.VISITED

        // Calcular racha (hacia atrás desde el último)
        var streak = 0
        for (c in completed) {
            if (c.currentStatus == lastCompleted.currentStatus) streak++
            else break
        }

        val badges = mutableListOf<GamificationBadge>()

        val visitedCount = completed.count { it.currentStatus == VisitStatus.VISITED }
        val failedCount = completed.count { it.currentStatus == VisitStatus.FAILED }

        // Calcular la racha cronológica y si se acaba de ganar ON_FIRE
        val completedAsc = completed.sortedBy { it.statusUpdatedAtMillis ?: 0L }
        var currentStreak = 0
        var justHitOnFire = false
        for (i in completedAsc.indices) {
            val c = completedAsc[i]
            if (c.currentStatus == VisitStatus.VISITED) {
                currentStreak++
                if (currentStreak == settings.gamificationOnFire) {
                    if (i == completedAsc.lastIndex) {
                        justHitOnFire = true
                    }
                    currentStreak = 0 // Reinicia para poder volver a ganarla
                }
            } else if (c.currentStatus == VisitStatus.FAILED) {
                currentStreak = 0
            }
        }

        // Se otorgan estrictamente cuando se alcanza el número exacto en la tarea actual.
        if (visitedCount == clients.size && visitedCount >= settings.gamificationSniper && failedCount == 0) {
            badges.add(GamificationBadge.SNIPER)
        }
        if (justHitOnFire) {
            badges.add(GamificationBadge.ON_FIRE)
        }
        // Solo si la última fue fallida y con eso alcanzamos el número exacto.
        if (lastCompleted.currentStatus == VisitStatus.FAILED && failedCount == settings.gamificationBlackCloud) {
            badges.add(GamificationBadge.UNLUCKY)
        }

        val message = if (isSuccess) {
            if (streak >= 3) successRachaMessages.random() else successNormalMessages.random()
        } else {
            failureMessages.random()
        }

        return GamificationStatus(badges, message)
    }

    fun getBadgeLabel(badge: GamificationBadge): String = when(badge) {
        GamificationBadge.ON_FIRE -> "🔥 Empleado en Llamas"
        GamificationBadge.SNIPER -> "🎯 Francotirador (100%)"
        GamificationBadge.SNAIL -> "🐢 Premio a la Tortuga"
        GamificationBadge.UNLUCKY -> "🌧️ Nube Negra"
    }

    fun getBadgeDescription(badge: GamificationBadge, settings: BusinessSettings): String = when(badge) {
        GamificationBadge.ON_FIRE -> "Has completado una racha de ${settings.gamificationOnFire} tareas exitosas consecutivas. ¡Imparable!"
        GamificationBadge.SNIPER -> "Has completado TODAS tus tareas exitosamente (mínimo ${settings.gamificationSniper}). Precisión letal."
        GamificationBadge.SNAIL -> "Has acumulado más de ${settings.gamificationTurtle} minutos de inactividad durante tu ruta. ¡Cuidado!"
        GamificationBadge.UNLUCKY -> "Has marcado ${settings.gamificationBlackCloud} o más tareas como fallidas. ¡Mejor suerte para la próxima!"
    }

    fun evaluateSummary(summary: com.wtracker.field.services.models.ShiftSummary, nowMillis: Long, settings: BusinessSettings): List<GamificationBadge> {
        val badges = mutableSetOf<GamificationBadge>()
        if (!settings.gamificationEnabled) return emptyList()

        if (summary.visitedCount == summary.totalTasksCount && summary.visitedCount >= settings.gamificationSniper && summary.failedCount == 0) {
            badges.add(GamificationBadge.SNIPER)
        }
        if (summary.maxSuccessStreak >= settings.gamificationOnFire) {
            badges.add(GamificationBadge.ON_FIRE)
        }
        if (summary.failedCount >= settings.gamificationBlackCloud) {
            badges.add(GamificationBadge.UNLUCKY)
        }
        if (snailLevel(summary, nowMillis, settings) >= 1) {
            badges.add(GamificationBadge.SNAIL)
        }
        return badges.toList()
    }

    /**
     * Veces que la inactividad acumulada de la ruta alcanzó el umbral de la Tortuga:
     * 1 = primera vez (se avisa), 2 = el doble del umbral (x2), etc. 0 si aún no la gana.
     */
    fun snailLevel(summary: com.wtracker.field.services.models.ShiftSummary, nowMillis: Long, settings: BusinessSettings): Int {
        if (!settings.gamificationEnabled) return 0
        val thresholdMillis = settings.gamificationTurtle * 60 * 1000L
        if (thresholdMillis <= 0L) return 0
        val stoppedMillis = summary.totalStoppedMillis(nowMillis)
        if (stoppedMillis <= thresholdMillis) return 0
        return (stoppedMillis / thresholdMillis).toInt().coerceAtLeast(1)
    }
}
