package com.aloharoha.focus

data class ThemeColors(val bg: String, val point: String, val text: String) {
    companion object {
        fun get(name: String): ThemeColors = when (name) {
            "Light" -> ThemeColors("#FFFFFF", "#2563EB", "#111111")
            "Dark" -> ThemeColors("#0F1115", "#4F8CFF", "#E8EAF0")
            "Dark Orange" -> ThemeColors("#0F1115", "#FF8C1A", "#F4EFE6")
            "Purple" -> ThemeColors("#1A1625", "#A855F7", "#E8E6F0")
            "Green" -> ThemeColors("#0F1F0F", "#22C55E", "#E8F5E8")
            "Rose" -> ThemeColors("#FFF1F2", "#E11D48", "#881337")
            "Ocean" -> ThemeColors("#0C1420", "#0EA5E9", "#E2E8F0")
            "Warm" -> ThemeColors("#FEFCFB", "#EA580C", "#78350F")
            else -> ThemeColors("#FFFFFF", "#2563EB", "#111111")
        }
    }
}