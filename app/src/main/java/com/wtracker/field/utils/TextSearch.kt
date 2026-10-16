package com.wtracker.field.utils

import java.text.Normalizer
import java.util.Locale

/** Búsqueda tolerante a mayúsculas y tildes ("panaderia" encuentra "Panadería"). */
object TextSearch {

    private val diacriticsRegex = Regex("\\p{Mn}+")
    private val searchLocale: Locale = Locale("es")

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(diacriticsRegex, "").lowercase(searchLocale).trim()

    private val whitespaceRegex = Regex("\\s+")

    /** Cada palabra de la consulta debe aparecer en algún campo; el orden de las palabras no importa. */
    fun matches(query: String, vararg fields: String?): Boolean {
        val queryTokens = normalize(query).split(whitespaceRegex).filter { it.isNotEmpty() }
        if (queryTokens.isEmpty()) return true
        val normalizedFields = fields.filterNotNull().map { normalize(it) }
        return queryTokens.all { token -> normalizedFields.any { field -> field.contains(token) } }
    }
}
