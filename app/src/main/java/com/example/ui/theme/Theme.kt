package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class LedgerSemanticColors(
    val revenue: Color,
    val revenueContainer: Color,
    val supply: Color,
    val supplyContainer: Color,
    val profitPositive: Color,
    val profitPositiveContainer: Color,
    val profitNegative: Color,
    val profitNegativeContainer: Color,
    val purchase: Color,
    val purchaseContainer: Color,
    val stockBadgeBlue: Color,
    val stockBadgeOnBlue: Color
)

private val LightLedgerColors = LedgerSemanticColors(
    revenue = IndigoPrimary,
    revenueContainer = IndigoContainer,
    supply = AmberWarm,
    supplyContainer = AmberContainer,
    profitPositive = MintEmerald,
    profitPositiveContainer = MintContainer,
    profitNegative = CoralAccent,
    profitNegativeContainer = CoralContainer,
    purchase = VioletPurchase,
    purchaseContainer = VioletContainer,
    stockBadgeBlue = StockBadgeBlueLight,
    stockBadgeOnBlue = StockBadgeOnBlue
)

private val DarkLedgerColors = LedgerSemanticColors(
    revenue = DarkIndigoPrimary,
    revenueContainer = DarkIndigoContainer,
    supply = DarkAmberWarm,
    supplyContainer = DarkAmberContainer,
    profitPositive = DarkMintEmerald,
    profitPositiveContainer = DarkMintContainer,
    profitNegative = DarkCoralAccent,
    profitNegativeContainer = DarkCoralContainer,
    purchase = DarkVioletPurchase,
    purchaseContainer = DarkVioletContainer,
    stockBadgeBlue = StockBadgeBlueDark,
    stockBadgeOnBlue = StockBadgeOnBlue
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }

private val DarkColorScheme = darkColorScheme(
    primary = DarkIndigoPrimary,
    onPrimary = IndigoDeep,
    primaryContainer = DarkIndigoContainer,
    onPrimaryContainer = Color.White,
    secondary = DarkCoralAccent,
    onSecondary = Color(0xFF420614),
    secondaryContainer = DarkCoralContainer,
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = DarkMintEmerald,
    onTertiary = Color(0xFF003824),
    tertiaryContainer = DarkMintContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoOnContainer,
    secondary = CoralAccent,
    onSecondary = Color.White,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = CoralOnContainer,
    tertiary = MintEmerald,
    onTertiary = Color.White,
    tertiaryContainer = MintContainer,
    onTertiaryContainer = MintOnContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

@Composable
fun ClawManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val ledgerColors = if (darkTheme) DarkLedgerColors else LightLedgerColors

    CompositionLocalProvider(LocalLedgerColors provides ledgerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
