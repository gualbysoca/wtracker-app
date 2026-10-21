package com.wtracker.field.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wtracker.field.ui.theme.WTheme

data class CountryDialCode(
    val isoCode: String,
    val name: String,
    val dialCode: String,
    val flagEmoji: String,
)

object CountryCodes {
    val BOLIVIA = CountryDialCode("BO", "Bolivia", "+591", "🇧🇴")

    val ALL: List<CountryDialCode> = listOf(
        BOLIVIA,
        CountryDialCode("AR", "Argentina", "+54", "🇦🇷"),
        CountryDialCode("BR", "Brasil", "+55", "🇧🇷"),
        CountryDialCode("CL", "Chile", "+56", "🇨🇱"),
        CountryDialCode("CO", "Colombia", "+57", "🇨🇴"),
        CountryDialCode("EC", "Ecuador", "+593", "🇪🇨"),
        CountryDialCode("PE", "Perú", "+51", "🇵🇪"),
        CountryDialCode("PY", "Paraguay", "+595", "🇵🇾"),
        CountryDialCode("UY", "Uruguay", "+598", "🇺🇾"),
        CountryDialCode("VE", "Venezuela", "+58", "🇻🇪"),
        CountryDialCode("MX", "México", "+52", "🇲🇽"),
        CountryDialCode("US", "Estados Unidos", "+1", "🇺🇸"),
        CountryDialCode("ES", "España", "+34", "🇪🇸"),
        CountryDialCode("PA", "Panamá", "+507", "🇵🇦"),
        CountryDialCode("CR", "Costa Rica", "+506", "🇨🇷"),
        CountryDialCode("DO", "Rep. Dominicana", "+1", "🇩🇴"),
        CountryDialCode("GT", "Guatemala", "+502", "🇬🇹"),
        CountryDialCode("HN", "Honduras", "+504", "🇭🇳"),
        CountryDialCode("NI", "Nicaragua", "+505", "🇳🇮"),
        CountryDialCode("SV", "El Salvador", "+503", "🇸🇻"),
    )

    fun findByDialCode(dialCode: String): CountryDialCode {
        val clean = dialCode.trim()
        return ALL.firstOrNull { it.dialCode == clean } ?: BOLIVIA
    }

    /**
     * Parsea un string telefónico completo (ej: "+591 70000001", "+59170000001" o "70000001")
     * devolviendo el código de país coincidente y el resto como número local.
     */
    fun splitPhone(rawPhone: String?): Pair<CountryDialCode, String> {
        val trimmed = rawPhone?.trim().orEmpty()
        if (trimmed.isBlank()) return Pair(BOLIVIA, "")

        val sortedByLength = ALL.sortedByDescending { it.dialCode.length }
        for (country in sortedByLength) {
            if (trimmed.startsWith(country.dialCode)) {
                val remainder = trimmed.removePrefix(country.dialCode).trim()
                return Pair(country, remainder)
            }
        }

        return Pair(BOLIVIA, trimmed)
    }
}

/**
 * Campo telefónico con selector modal de código de país por defecto en Bolivia (+591).
 */
@Composable
fun CountryCodePhoneField(
    dialCode: String,
    onDialCodeChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing
    var isPickerOpen by remember { mutableStateOf(false) }

    val selectedCountry = remember(dialCode) { CountryCodes.findByDialCode(dialCode) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        // Selector de País / Código
        Surface(
            shape = RoundedCornerShape(WTheme.radius.md),
            color = colors.surface,
            border = BorderStroke(WTheme.sizes.borderThin, colors.border),
            modifier = Modifier
                .height(56.dp)
                .clickable(enabled = enabled) { isPickerOpen = true },
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selectedCountry.flagEmoji,
                    style = WTheme.typography.subtitle,
                )
                Text(
                    text = selectedCountry.dialCode,
                    style = WTheme.typography.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "Cambiar país",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Campo de texto del número local
        WTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = label,
            focusRequester = focusRequester,
            errorMessage = errorMessage,
            enabled = enabled,
            leadingIcon = Icons.Outlined.Phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    if (isPickerOpen) {
        CountryCodePickerDialog(
            selectedDialCode = selectedCountry.dialCode,
            onSelect = { country ->
                onDialCodeChange(country.dialCode)
                isPickerOpen = false
            },
            onDismiss = { isPickerOpen = false },
        )
    }
}

@Composable
private fun CountryCodePickerDialog(
    selectedDialCode: String,
    onSelect: (CountryDialCode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WTheme.colors
    val spacing = WTheme.spacing
    var searchQuery by remember { mutableStateOf("") }

    val filteredCountries = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) CountryCodes.ALL
        else CountryCodes.ALL.filter {
            it.name.lowercase().contains(q) ||
            it.dialCode.contains(q) ||
            it.isoCode.lowercase().contains(q)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(WTheme.radius.xl),
            color = colors.surface,
            border = BorderStroke(WTheme.sizes.borderThin, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.md),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Seleccionar código de país",
                        style = WTheme.typography.subtitle.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Buscador
                WTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Buscar país o código...",
                    leadingIcon = Icons.Outlined.Search,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = colors.border, thickness = WTheme.sizes.borderThin)

                // Lista de países
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredCountries, key = { it.isoCode + it.dialCode }) { country ->
                        val isSelected = country.dialCode == selectedDialCode
                        val itemBg = if (isSelected) colors.surfaceElevated else Color.Transparent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(itemBg, RoundedCornerShape(WTheme.radius.sm))
                                .clickable { onSelect(country) }
                                .padding(horizontal = spacing.sm, vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = country.flagEmoji,
                                    style = WTheme.typography.title,
                                )
                                Text(
                                    text = country.name,
                                    style = WTheme.typography.body,
                                    color = colors.textPrimary,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                Text(
                                    text = country.dialCode,
                                    style = WTheme.typography.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) colors.accent else colors.textSecondary,
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Seleccionado",
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
