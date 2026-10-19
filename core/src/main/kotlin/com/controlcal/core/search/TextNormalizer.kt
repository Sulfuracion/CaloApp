package com.controlcal.core.search

import java.text.Normalizer

object TextNormalizer {

    private val DIACRITICS_REGEX = Regex("\\p{M}")
    private val MULTIPLE_WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Normalizes a string for search matching:
     * 1. Converts to lower-case.
     * 2. Decomposes characters to NFD and strips diacritics / combining marks
     *    (converting á, é, í, ó, ú, ü to a, e, i, o, u, and ñ / Ñ to n).
     * 3. Collapses multiple whitespace characters to a single space.
     * 4. Trims leading and trailing whitespace.
     */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val lower = input.lowercase()
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITICS_REGEX.replace(normalized, "")
        return MULTIPLE_WHITESPACE_REGEX.replace(withoutDiacritics, " ").trim()
    }
}
