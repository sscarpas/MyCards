package com.example.mycards

import androidx.core.graphics.toColorInt
import kotlin.math.abs

/**
 * Utilities for computing grid-thumbnail display labels and background colours.
 *
 * Label rules:
 *  - Single-word name          → show as-is          ("IKEA"     → "IKEA")
 *  - Multi-word, unique first  → show first word only ("Biedronka" → "Biedronka")
 *  - Multi-word, shared first  → first word + initials of remaining words
 *                                ("My Card Green" → "My CG", "My Card Blue" → "My CB")
 */
object CardLabelUtils {

    /**
     * 8 saturated Material Design colours used as thumbnail backgrounds.
     * Chosen to provide strong contrast with white text.
     */
    private val PALETTE = intArrayOf(
        "#00897B".toColorInt(), // Teal 600
        "#3949AB".toColorInt(), // Indigo 500
        "#E65100".toColorInt(), // Orange 900
        "#43A047".toColorInt(), // Green 600
        "#E53935".toColorInt(), // Red 600
        "#8E24AA".toColorInt(), // Purple 600
        "#6D4C41".toColorInt(), // Brown 500
        "#546E7A".toColorInt()  // Blue Grey 600
    )

    /**
     * Returns a background colour for [name] that is stable (same name → same colour)
     * and distributed across [PALETTE] via hashCode.
     */
    fun labelBackgroundColor(name: String): Int =
        PALETTE[abs(name.lowercase().hashCode()) % PALETTE.size]

    /**
     * Computes a display label for every card in [cards].
     *
     * @return Map of card id → display label string.
     */
    fun computeDisplayLabels(cards: List<LoyaltyCard>): Map<Int, String> {
        // Group cards by their first word (case-preserved for display, compared case-insensitively)
        val groups: Map<String, List<LoyaltyCard>> = cards.groupBy { firstWord(it.name) }

        return cards.associate { card ->
            val first = firstWord(card.name)
            val siblings = groups[first] ?: emptyList()

            val label = if (siblings.size <= 1) {
                // Unique first word — show it alone
                first
            } else {
                // Collision — append initials of remaining words
                val remaining = card.name.trim().split("\\s+".toRegex()).drop(1)
                if (remaining.isEmpty()) first
                else first + " " + remaining.joinToString("") { w -> w[0].uppercaseChar().toString() }
            }

            card.id to label
        }
    }

    private fun firstWord(name: String): String =
        name.trim().split("\\s+".toRegex()).firstOrNull() ?: name
}




