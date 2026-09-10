@file:Suppress("DEPRECATION")
package com.example.kaspawallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.Modifier
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider

private object NoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node() {}
    }

    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = other === this
}

private val DarkColorScheme = darkColorScheme(
    primary = KaspaPrimary,
    onPrimary = Color(0xFF003731),
    primaryContainer = KaspaSurfaceElevated,
    onPrimaryContainer = KaspaPrimaryGlow,
    secondary = KaspaSecondary,
    onSecondary = Color(0xFF003730),
    secondaryContainer = KaspaSurfaceVariant,
    onSecondaryContainer = KaspaTertiary,
    tertiary = KaspaTertiary,
    background = KaspaBackground,
    onBackground = KaspaTextPrimary,
    surface = KaspaSurface,
    onSurface = KaspaTextPrimary,
    surfaceVariant = KaspaSurfaceVariant,
    onSurfaceVariant = KaspaTextSecondary,
    outline = KaspaCardBorder,
    error = KaspaError,
    onError = Color.White
)

@Composable
fun KaspaWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Kaspa Wallet design is built with a deep fintech dark aesthetic
    CompositionLocalProvider(LocalIndication provides NoRippleIndication) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}
