package com.wtracker.field.services.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import kotlinx.serialization.json.JsonElement

@Serializable
data class SettingsDto(
    @SerialName("gamification_enabled") val gamificationEnabled: Boolean = true,
    @SerialName("gamification_sniper") val gamificationSniper: JsonElement? = null,
    @SerialName("gamification_on_fire") val gamificationOnFire: JsonElement? = null,
    @SerialName("gamification_black_cloud") val gamificationBlackCloud: JsonElement? = null,
    @SerialName("gamification_turtle") val gamificationTurtle: JsonElement? = null,
)
