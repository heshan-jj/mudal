// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-22 - see NOTICE.
package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.graphics.Color

// Mudal palette - the tonal set seeded from the logo green, shared with the website
// (website/src/styles/global.css). The website has no dark mode; the dark set is the
// same hues lifted for dark surfaces.
val mudal_green = Color(0xFF2F6A5B)
val mudal_green_strong = Color(0xFF1E5147)
val mudal_mint = Color(0xFFB8EAD9)
val mudal_sage = Color(0xFF8BBDAE)
val mudal_cream = Color(0xFFF7F5EF)

// Light Theme Colors
val md_theme_light_primary = mudal_green
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = mudal_mint
val md_theme_light_onPrimaryContainer = Color(0xFF0B3A2F)
val md_theme_light_secondary = Color(0xFF4B635A)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFD3E8DE)
val md_theme_light_onSecondaryContainer = Color(0xFF173A30)
val md_theme_light_tertiary = Color(0xFF9A4A1E)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFDBC8)
val md_theme_light_onTertiaryContainer = Color(0xFF3A1604)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_outline = Color(0xFF737970)
val md_theme_light_background = mudal_cream
val md_theme_light_onBackground = Color(0xFF191C1A)
val md_theme_light_surface = Color(0xFFFDFCF8)
val md_theme_light_onSurface = Color(0xFF191C1A)
val md_theme_light_surfaceVariant = Color(0xFFEBE9E0)
val md_theme_light_onSurfaceVariant = Color(0xFF46524C)
val md_theme_light_inverseSurface = Color(0xFF2E312F)
val md_theme_light_inverseOnSurface = Color(0xFFEFF1EC)
val md_theme_light_inversePrimary = mudal_sage
val md_theme_light_surfaceTint = mudal_green
val md_theme_light_outlineVariant = Color(0xFFC4C8BF)
val md_theme_light_scrim = Color(0xFF000000)
val md_theme_light_surfaceBright = Color(0xFFFDFCF8)
val md_theme_light_surfaceDim = Color(0xFFDCDAD2)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF2F0E8)
val md_theme_light_surfaceContainer = Color(0xFFEBE9E0)
val md_theme_light_surfaceContainerHigh = Color(0xFFE3E1D7)
val md_theme_light_surfaceContainerHighest = Color(0xFFDCDACF)

// Dark Theme Colors
val md_theme_dark_primary = mudal_sage
val md_theme_dark_onPrimary = Color(0xFF00382D)
val md_theme_dark_primaryContainer = mudal_green_strong
val md_theme_dark_onPrimaryContainer = mudal_mint
val md_theme_dark_secondary = Color(0xFFB7CCC3)
val md_theme_dark_onSecondary = Color(0xFF223530)
val md_theme_dark_secondaryContainer = Color(0xFF384B45)
val md_theme_dark_onSecondaryContainer = Color(0xFFD3E8DE)
val md_theme_dark_tertiary = Color(0xFFFFB68E)
val md_theme_dark_onTertiary = Color(0xFF562000)
val md_theme_dark_tertiaryContainer = Color(0xFF7A3410)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFDBC8)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_outline = Color(0xFF89938D)
val md_theme_dark_background = Color(0xFF101412)
val md_theme_dark_onBackground = Color(0xFFE0E3DF)
val md_theme_dark_surface = Color(0xFF101412)
val md_theme_dark_onSurface = Color(0xFFE0E3DF)
val md_theme_dark_surfaceVariant = Color(0xFF3F4945)
val md_theme_dark_onSurfaceVariant = Color(0xFFBFC9C3)
val md_theme_dark_inverseSurface = Color(0xFFE0E3DF)
val md_theme_dark_inverseOnSurface = Color(0xFF2D312F)
val md_theme_dark_inversePrimary = mudal_green
val md_theme_dark_surfaceTint = mudal_sage
val md_theme_dark_outlineVariant = Color(0xFF3F4945)
val md_theme_dark_scrim = Color(0xFF000000)
val md_theme_dark_surfaceBright = Color(0xFF363B38)
val md_theme_dark_surfaceDim = Color(0xFF101412)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0B0F0D)
val md_theme_dark_surfaceContainerLow = Color(0xFF181D1B)
val md_theme_dark_surfaceContainer = Color(0xFF1C211F)
val md_theme_dark_surfaceContainerHigh = Color(0xFF272B29)
val md_theme_dark_surfaceContainerHighest = Color(0xFF313634)

// Custom Semantic Colors
val success_light = Color(0xFF2E7D32)  // Medium green for good contrast on white
val success_dark = Color(0xFF81C784)  // Lighter green for better contrast on dark backgrounds
val warning_light = Color(0xFFE65100)  // Darker orange for better contrast
val warning_dark = Color(0xFFFFB74D)  // Lighter orange for dark mode

// Transaction Type Colors
// Using distinct colors that work well on both light and dark backgrounds
val income_light = Color(0xFF00796B)  // Teal 700 - good on white
val income_dark = Color(0xFF80CBC4)   // Teal 200 - readable on dark backgrounds
val expense_light = Color(0xFFC62828) // Red 800 - good on white
val expense_dark = Color(0xFFE57373)  // Red 300 - readable on dark backgrounds
val credit_light = Color(0xFFEF6C00)  // Orange 800 for credit card - good on white
val credit_dark = Color(0xFFFFD54F)   // Amber 300 for credit card - readable on dark
val transfer_light = Color(0xFF303F9F) // Indigo 700 for transfers - good on white
val transfer_dark = Color(0xFF9FA8DA)  // Indigo 200 for transfers - readable on dark
val investment_light = Color(0xFF37474F) // Blue Grey 800 for investments - good on white
val investment_dark = Color(0xFF90A4AE)  // Blue Grey 300 for investments - readable on dark

// Loan Colors (Amber / Warm)
val loan_light = Color(0xFFFF8F00)           // Amber 800
val loan_dark = Color(0xFFFFCA28)            // Amber 400
val loan_container_light = Color(0xFFFFF8E1) // Amber 50
val loan_container_dark = Color(0xFF3E2723)  // Brown 900

// Category Colors
val category_food = Color(0xFFFF7043)  // Coral
val category_transport = Color(0xFF5C6BC0)  // Indigo
val category_shopping = Color(0xFFAB47BC)  // Purple
val category_bills = Color(0xFF42A5F5)  // Light Blue
val category_entertainment = Color(0xFFEC407A)  // Pink
val category_health = Color(0xFF26A69A)  // Teal
val category_other = Color(0xFF78909C)  // Blue Grey

// Budget Progress Colors
// Green: under 50% spent (safe)
val budget_safe_light = Color(0xFF2E7D32)   // Medium green - good contrast on white
val budget_safe_dark = Color(0xFF81C784)    // Light green - readable on dark

// Yellow/Amber: 50-80% spent (warning)
val budget_warning_light = Color(0xFFFF8F00)  // Amber - good contrast on white
val budget_warning_dark = Color(0xFFFFCA28)   // Light amber - readable on dark

// Red: over 80% spent (danger)
val budget_danger_light = Color(0xFFC62828)   // Red 800 - good contrast on white
val budget_danger_dark = Color(0xFFE57373)    // Red 300 - readable on dark

// Budget Card Colors (for different budget cards)
val budget_card_colors = listOf(
    Color(0xFF2F6A5B),  // Mudal green (default)
    Color(0xFF1565C0),  // Blue
    Color(0xFFF57C00),  // Orange
    Color(0xFF7B1FA2),  // Purple
    Color(0xFFD32F2F),  // Red
    Color(0xFF00838F),  // Cyan
    Color(0xFFC2185B),  // Pink
    Color(0xFF455A64)   // Blue Grey
)

// Rose Pine Dawn (Light) - accent palettes
val Dawn_Rose = Color(0xFFD7827E)
val Dawn_Rose_secondary = Color(0xFFD4A373)
val Dawn_Rose_tertiary = Color(0xFFC49B9B)

val Dawn_Iris = Color(0xFF907AA9)
val Dawn_Iris_secondary = Color(0xFF7E9CB9)
val Dawn_Iris_tertiary = Color(0xFFB4637A)

val Dawn_Pine = Color(0xFF286983)
val Dawn_Pine_secondary = Color(0xFF56949F)
val Dawn_Pine_tertiary = Color(0xFF3B8686)

val Dawn_Gold = Color(0xFFEA9D34)
val Dawn_Gold_secondary = Color(0xFFD7827E)
val Dawn_Gold_tertiary = Color(0xFFC4956A)

val Dawn_Love = Color(0xFFB4637A)
val Dawn_Love_secondary = Color(0xFFD7827E)
val Dawn_Love_tertiary = Color(0xFF907AA9)

val Dawn_Foam = Color(0xFF56949F)
val Dawn_Foam_secondary = Color(0xFF286983)
val Dawn_Foam_tertiary = Color(0xFF6E9A6E)

val Dawn_Muted = Color(0xFF797593)
val Dawn_Muted_secondary = Color(0xFF9893A5)
val Dawn_Muted_tertiary = Color(0xFF6E6A86)

val Dawn_Subtle = Color(0xFF908CAA)
val Dawn_Subtle_secondary = Color(0xFF797593)
val Dawn_Subtle_tertiary = Color(0xFFA39FBF)

val Dawn_Text = Color(0xFF575279)
val Dawn_Text_secondary = Color(0xFF6E6A86)
val Dawn_Text_tertiary = Color(0xFF4A4568)

val Dawn_Highlight = Color(0xFFC4A7E7)
val Dawn_Highlight_secondary = Color(0xFF907AA9)
val Dawn_Highlight_tertiary = Color(0xFFB4A0D1)

val Dawn_Surface = Color(0xFF6E6A86)
val Dawn_Surface_secondary = Color(0xFF797593)
val Dawn_Surface_tertiary = Color(0xFF575279)

val Dawn_Overlay = Color(0xFF9893A5)
val Dawn_Overlay_secondary = Color(0xFF908CAA)
val Dawn_Overlay_tertiary = Color(0xFF7E7A96)

// Rose Pine Main (Dark) - accent palettes
val RosePine_Rose = Color(0xFFEBBCBA)
val RosePine_Rose_secondary = Color(0xFFF6C177)
val RosePine_Rose_tertiary = Color(0xFFE0AFA0)

val RosePine_Iris = Color(0xFFC4A7E7)
val RosePine_Iris_secondary = Color(0xFF9CCFD8)
val RosePine_Iris_tertiary = Color(0xFFEB6F92)

val RosePine_Pine = Color(0xFF31748F)
val RosePine_Pine_secondary = Color(0xFF9CCFD8)
val RosePine_Pine_tertiary = Color(0xFF569F9F)

val RosePine_Gold = Color(0xFFF6C177)
val RosePine_Gold_secondary = Color(0xFFEBBCBA)
val RosePine_Gold_tertiary = Color(0xFFE0B08A)

val RosePine_Love = Color(0xFFEB6F92)
val RosePine_Love_secondary = Color(0xFFEBBCBA)
val RosePine_Love_tertiary = Color(0xFFC4A7E7)

val RosePine_Foam = Color(0xFF9CCFD8)
val RosePine_Foam_secondary = Color(0xFF31748F)
val RosePine_Foam_tertiary = Color(0xFF8EC9A0)

val RosePine_Muted = Color(0xFF6E6A86)
val RosePine_Muted_secondary = Color(0xFF908CAA)
val RosePine_Muted_tertiary = Color(0xFF56526E)

val RosePine_Subtle = Color(0xFF908CAA)
val RosePine_Subtle_secondary = Color(0xFF6E6A86)
val RosePine_Subtle_tertiary = Color(0xFFABA6C4)

val RosePine_Text = Color(0xFFE0DEF4)
val RosePine_Text_secondary = Color(0xFF908CAA)
val RosePine_Text_tertiary = Color(0xFFC8C6DD)

val RosePine_Highlight = Color(0xFFC4A7E7)
val RosePine_Highlight_secondary = Color(0xFFEB6F92)
val RosePine_Highlight_tertiary = Color(0xFFB4A0D1)

val RosePine_Surface = Color(0xFF403D52)
val RosePine_Surface_secondary = Color(0xFF524F67)
val RosePine_Surface_tertiary = Color(0xFF2A2837)

val RosePine_Overlay = Color(0xFF524F67)
val RosePine_Overlay_secondary = Color(0xFF6E6A86)
val RosePine_Overlay_tertiary = Color(0xFF403D52)

// Settings Icon Background Colors
val amber_light = Color(0xFFFFF8E1)
val amber_dark = Color(0xFFFF8F00)
val orange_light = Color(0xFFFFF3E0)
val orange_dark = Color(0xFFE65100)
val green_light = Color(0xFFE8F5E9)
val green_dark = Color(0xFF2E7D32)
val teal_light = Color(0xFFE0F2F1)
val teal_dark = Color(0xFF00695C)
val blue_light = Color(0xFFE3F2FD)
val blue_dark = Color(0xFF1565C0)
val indigo_light = Color(0xFFE8EAF6)
val indigo_dark = Color(0xFF283593)
val red_light = Color(0xFFFFEBEE)
val red_dark = Color(0xFFC62828)
val pink_light = Color(0xFFFCE4EC)
val pink_dark = Color(0xFFAD1457)
val purple_light = Color(0xFFF3E5F5)
val purple_dark = Color(0xFF6A1B9A)
val cyan_light = Color(0xFFE0F7FA)
val cyan_dark = Color(0xFF00838F)
val yellow_light = Color(0xFFFFF8E1)
val yellow_dark = Color(0xFFF57F17)
/** Body text on a [yellow_light] surface — readable in both themes since the surface never darkens. */
val yellow_on_light = Color(0xFF3A2B00)
/** Pro tonal card in dark theme: a deep gold surface so it sits with the other cards. */
val yellow_surface_dark = Color(0xFF2E2614)
/** Text on [yellow_surface_dark]. */
val yellow_on_dark = Color(0xFFFFE6A8)
/** Accent (icon, action) on [yellow_surface_dark]. */
val yellow_accent_dark = Color(0xFFFFC24A)
val grey_light = Color(0xFFECEFF1)
val grey_dark = Color(0xFF546E7A)

// AMOLED Dark Mode Colors
val amoled_background = Color(0xFF000000)
val amoled_surface = Color(0xFF1A1A1A)
val amoled_surfaceVariant = Color(0xFF242424)
val amoled_surfaceContainerLowest = Color(0xFF000000)
val amoled_surfaceContainerLow = Color(0xFF141414)
val amoled_surfaceContainer = Color(0xFF1C1C1C)
val amoled_surfaceContainerHigh = Color(0xFF272727)
val amoled_surfaceContainerHighest = Color(0xFF323232)
val amoled_surfaceBright = Color(0xFF2A2A2A)
val amoled_surfaceDim = Color(0xFF0A0A0A)
