package dev.hyprconnect.app.ui.theme

import androidx.compose.ui.graphics.Color

// Catppuccin Mocha — The most popular Hyprland color scheme
// Base surfaces (darkest to lightest)
val HyprCrust    = Color(0xFF11111b)  // Deepest background
val HyprMantle   = Color(0xFF181825)  // App surface
val HyprBase     = Color(0xFF1e1e2e)  // Main background
val HyprSurface0 = Color(0xFF313244)  // Card / elevated surface
val HyprSurface1 = Color(0xFF45475a)  // Higher elevation
val HyprSurface2 = Color(0xFF585b70)  // Highest elevation / dividers

// Overlay / muted
val HyprOverlay0 = Color(0xFF6c7086)  // Disabled / placeholder
val HyprOverlay1 = Color(0xFF7f849c)  // Subtle muted text

// Text
val HyprText     = Color(0xFFcdd6f4)  // Primary text
val HyprSubtext1 = Color(0xFFbac2de)  // Secondary text
val HyprSubtext0 = Color(0xFFa6adc8)  // Muted / hint text

// Accent colors
val HyprBlue     = Color(0xFF89b4fa)  // Primary — Hyprland default border
val HyprLavender = Color(0xFFb4befe)  // Secondary / lavender
val HyprMauve    = Color(0xFFcba6f7)  // Tertiary / purple
val HyprPink     = Color(0xFFf38ba8)  // Error / danger / red
val HyprTeal     = Color(0xFF94e2d5)  // Connected / success
val HyprSky      = Color(0xFF89dceb)  // Info / sky blue
val HyprGreen    = Color(0xFFa6e3a1)  // Positive / online
val HyprYellow   = Color(0xFFf9e2af)  // Warning
val HyprPeach    = Color(0xFFfab387)  // Orange accent

// Dark tinted containers (for Card backgrounds with accent tint)
val HyprBlueContainer   = Color(0xFF1a2744)
val HyprMauveContainer  = Color(0xFF261b3a)
val HyprTealContainer   = Color(0xFF0d2323)
val HyprPinkContainer   = Color(0xFF3a0d18)

// Glassmorphism palette: translucent fills + thin highlight borders that
// sit over the gradient backdrop applied at the screen root.
val HyprGlass         = Color(0x66313244)   // ~40% Surface0 — primary card fill
val HyprGlassDeep     = Color(0x80181825)   // ~50% Mantle — top bars, dialogs
val HyprGlassRaised   = Color(0x6645475a)   // ~40% Surface1 — emphasis surface
val HyprGlassBorder   = Color(0x33cdd6f4)   // ~20% Text — subtle hairline edge
val HyprGlassHighlight = Color(0x14ffffff)  // ~8% white — top inner glow
val HyprBackdropTop    = Color(0xFF1e1e2e)  // gradient start (HyprBase)
val HyprBackdropBottom = Color(0xFF11111b)  // gradient end (HyprCrust)
