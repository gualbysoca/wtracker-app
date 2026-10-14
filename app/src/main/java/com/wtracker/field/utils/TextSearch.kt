package com.wtracker.field.utils

import java.text.Normalizer
import java.util.Locale

/** Búsqueda tolerante a mayúsculas y tildes ("panaderia" encuentra "Panadería"). */
object TextSearch {

    private val diacriticsRegex = Regex("\\p{Mn}+")
    private val searchLocale: Locale = Locale("es")

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(diacriticsRegex, "").lowercase(searchLocale).trim()

    fun matches(query: String, vararg fields: String?): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return true
        return fields.any { field -> field != null && normalize(field).contains(normalizedQuery) }
    }
}
