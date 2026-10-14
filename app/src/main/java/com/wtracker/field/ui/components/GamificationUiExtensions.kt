package com.wtracker.field.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.wtracker.field.R
import com.wtracker.field.utils.GamificationBadge

import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Snooze

@Composable
fun GamificationBadge.getIcon(): ImageVector {
    return when (this) {
        GamificationBadge.ON_FIRE -> Icons.Outlined.MilitaryTech
        GamificationBadge.SNIPER -> Icons.Outlined.WorkspacePremium
        GamificationBadge.SNAIL -> Icons.Outlined.Snooze
        GamificationBadge.UNLUCKY -> ImageVector.vectorResource(R.drawable.ic_skull)
    }
}

fun GamificationBadge.isPositive(): Boolean {
    return this == GamificationBadge.ON_FIRE || this == GamificationBadge.SNIPER
}
