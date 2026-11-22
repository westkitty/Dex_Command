package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class DexSemanticColors(
    val verified: Color,
    val verifiedBg: Color,
    val blocked: Color,
    val blockedBg: Color,
    val review: Color,
    val reviewBg: Color,
    val claimed: Color,
    val claimedBg: Color,
    val paused: Color,
    val pausedBg: Color,
    val archived: Color,
    val archivedBg: Color,
    val demoAccent: Color,
    val demoBg: Color,
    val hairline: Color,
    val reducedMotion: Boolean,
    val lowStimulation: Boolean
)

val LocalDexColors = staticCompositionLocalOf {
    DexSemanticColors(
        verified = StatusVerified,
        verifiedBg = StatusVerifiedBg,
        blocked = StatusBlocked,
        blockedBg = StatusBlockedBg,
        review = StatusReview,
        reviewBg = StatusReviewBg,
        claimed = StatusClaimed,
        claimedBg = StatusClaimedBg,
        paused = StatusPaused,
        pausedBg = StatusPausedBg,
        archived = StatusArchived,
        archivedBg = StatusArchivedBg,
        demoAccent = DemoBadgeAccent,
        demoBg = DemoBadgeBg,
        hairline = CharcoalOutline,
        reducedMotion = false,
        lowStimulation = false
    )
}

private val StandardCommandColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanHighlight,
    secondary = SlateInkSecondary,
    onSecondary = CharcoalVoid,
    secondaryContainer = CharcoalElevated,
    onSecondaryContainer = SlateInkPrimary,
    tertiary = StatusVerified,
    onTertiary = CharcoalVoid,
    tertiaryContainer = StatusVerifiedBg,
    onTertiaryContainer = StatusVerified,
    background = CharcoalVoid,
    onBackground = SlateInkPrimary,
    surface = CharcoalSurface,
    onSurface = SlateInkPrimary,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = SlateInkSecondary,
    surfaceContainerLowest = CharcoalVoid,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalCard,
    surfaceContainerHigh = CharcoalElevated,
    surfaceContainerHighest = Color(0xFF283445),
    outline = CharcoalOutline,
    outlineVariant = CharcoalOutlineSubtle,
    error = StatusBlocked,
    onError = CharcoalVoid,
    errorContainer = StatusBlockedBg,
    onErrorContainer = StatusBlocked
)

private val LowStimCommandColorScheme = darkColorScheme(
    primary = LowStimAccent,
    onPrimary = LowStimCanvas,
    primaryContainer = LowStimAccentContainer,
    onPrimaryContainer = LowStimInkPrimary,
    secondary = LowStimInkSecondary,
    onSecondary = LowStimCanvas,
    secondaryContainer = LowStimCard,
    onSecondaryContainer = LowStimInkPrimary,
    tertiary = LowStimVerified,
    onTertiary = LowStimCanvas,
    tertiaryContainer = Color(0xFF1E2925),
    onTertiaryContainer = LowStimVerified,
    background = LowStimCanvas,
    onBackground = LowStimInkPrimary,
    surface = LowStimSurface,
    onSurface = LowStimInkPrimary,
    surfaceVariant = LowStimCard,
    onSurfaceVariant = LowStimInkSecondary,
    surfaceContainerLowest = LowStimCanvas,
    surfaceContainerLow = LowStimSurface,
    surfaceContainer = LowStimCard,
    surfaceContainerHigh = Color(0xFF282E38),
    surfaceContainerHighest = Color(0xFF303742),
    outline = LowStimOutline,
    outlineVariant = Color(0xFF262C36),
    error = LowStimBlocked,
    onError = LowStimCanvas,
    errorContainer = Color(0xFF2A1F20),
    onErrorContainer = LowStimBlocked
)

val DexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun DexCommandTheme(
    lowStimulation: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (lowStimulation) LowStimCommandColorScheme else StandardCommandColorScheme
    val semanticColors = if (lowStimulation) {
        DexSemanticColors(
            verified = LowStimVerified,
            verifiedBg = Color(0xFF1D2824),
            blocked = LowStimBlocked,
            blockedBg = Color(0xFF2A1E20),
            review = LowStimReview,
            reviewBg = Color(0xFF29251A),
            claimed = LowStimClaimed,
            claimedBg = Color(0xFF1D252E),
            paused = LowStimInkSecondary,
            pausedBg = Color(0xFF22272F),
            archived = Color(0xFF707A85),
            archivedBg = Color(0xFF1C2026),
            demoAccent = LowStimReview,
            demoBg = Color(0xFF26231B),
            hairline = LowStimOutline,
            reducedMotion = reducedMotion,
            lowStimulation = true
        )
    } else {
        DexSemanticColors(
            verified = StatusVerified,
            verifiedBg = StatusVerifiedBg,
            blocked = StatusBlocked,
            blockedBg = StatusBlockedBg,
            review = StatusReview,
            reviewBg = StatusReviewBg,
            claimed = StatusClaimed,
            claimedBg = StatusClaimedBg,
            paused = StatusPaused,
            pausedBg = StatusPausedBg,
            archived = StatusArchived,
            archivedBg = StatusArchivedBg,
            demoAccent = DemoBadgeAccent,
            demoBg = DemoBadgeBg,
            hairline = CharcoalOutline,
            reducedMotion = reducedMotion,
            lowStimulation = false
        )
    }

    CompositionLocalProvider(LocalDexColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DexTypography,
            shapes = DexShapes,
            content = content
        )
    }
}
