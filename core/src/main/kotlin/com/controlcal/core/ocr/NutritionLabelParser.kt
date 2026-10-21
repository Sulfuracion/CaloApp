package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.OcrBox
import com.controlcal.core.ocr.model.OcrLine
import com.controlcal.core.ocr.model.OcrParsedNutrients
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object NutritionLabelParser {

    private enum class NutrientType(val fieldName: String) {
        ENERGY("kcal"),
        FAT("fat"),
        SAT_FAT("satFat"),
        CARBS("carbs"),
        SUGARS("sugars"),
        FIBER("fiber"),
        PROTEIN("protein"),
        SALT("salt")
    }

    private data class ParsedValue(
        val value: Double,
        val isApproximate: Boolean = false,
        val fromKj: Boolean = false,
        val inferredDecimal: Boolean = false,
        val rawToken: String = ""
    )

    /**
     * Parses the lines detected by OCR to extract nutritional values per 100g or 100ml
     * following the 8 steps in Section 5.3 of PLAN.md.
     */
    fun parse(lines: List<OcrLine>): OcrParsedNutrients? {
        if (lines.isEmpty()) return null

        // Step 1: Pre-normalize lines
        val normalizedLines = lines.map { line ->
            NormalizedLine(
                original = line,
                normalizedText = normalizeText(line.text)
            )
        }

        // Step 4: Detect columns ("100 g" / "100 ml" vs portion columns)
        val col100gBox = find100gColumnBox(normalizedLines)
        val portionBoxes = if (col100gBox != null) {
            findPortionColumnBoxes(normalizedLines, col100gBox)
        } else {
            emptyList()
        }

        // Step 2: Locate rows by keyword
        val detectedRows = locateNutrientRows(normalizedLines, col100gBox)
        if (detectedRows.isEmpty()) return null

        // Steps 3, 5, 6: Extract values for each detected nutrient
        val extractedValues = mutableMapOf<NutrientType, ParsedValue>()
        val usedLines = mutableSetOf<NormalizedLine>()

        for ((type, keywordLine) in detectedRows) {
            val parsedValue = extractNutrientValue(
                type = type,
                keywordLine = keywordLine,
                allLines = normalizedLines,
                col100gBox = col100gBox,
                portionBoxes = portionBoxes,
                usedLines = usedLines
            )
            if (parsedValue != null) {
                extractedValues[type] = parsedValue
            }
        }

        if (extractedValues.isEmpty()) return null

        // Refine OCR character confusions in numeric context (Step 1 & 7)
        refineDecimalValues(extractedValues)

        // Build initial OcrParsedNutrients
        val approximations = mutableSetOf<String>()
        val kcalVal = extractedValues[NutrientType.ENERGY]
        val proteinVal = extractedValues[NutrientType.PROTEIN]
        val carbsVal = extractedValues[NutrientType.CARBS]
        val fatVal = extractedValues[NutrientType.FAT]
        val sugarsVal = extractedValues[NutrientType.SUGARS]
        val satFatVal = extractedValues[NutrientType.SAT_FAT]
        val fiberVal = extractedValues[NutrientType.FIBER]
        val saltVal = extractedValues[NutrientType.SALT]

        for ((type, parsed) in extractedValues) {
            if (parsed.isApproximate) {
                approximations.add(type.fieldName)
            }
        }

        val rawNutrients = OcrParsedNutrients(
            kcal = kcalVal?.value,
            protein = proteinVal?.value,
            carbs = carbsVal?.value,
            fat = fatVal?.value,
            sugars = sugarsVal?.value,
            satFat = satFatVal?.value,
            fiber = fiberVal?.value,
            salt = saltVal?.value,
            approximations = approximations
        )

        // Step 7: Validation using LabelValidator
        val validationResult = LabelValidator.validate(rawNutrients)

        // Step 8: Confidence scoring
        val confidenceMap = mutableMapOf<String, Float>()
        for ((type, parsed) in extractedValues) {
            val field = type.fieldName
            var conf = 0.95f
            if (parsed.isApproximate) conf = 0.90f
            if (parsed.fromKj) conf = 0.85f
            if (parsed.inferredDecimal) conf = min(conf, 0.80f)
            if (validationResult.invalidFields.contains(field)) {
                conf = 0.40f
            }
            confidenceMap[field] = conf
        }

        return rawNutrients.copy(confidence = confidenceMap)
    }

    private data class NormalizedLine(
        val original: OcrLine,
        val normalizedText: String,
        val box: OcrBox = original.box
    )

    // Step 1: Text normalization
    private fun normalizeText(text: String): String {
        val lower = text.lowercase()
        val nfd = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    // Step 4: Locate 100g / 100ml column header box
    private fun find100gColumnBox(lines: List<NormalizedLine>): OcrBox? {
        val patterns = listOf(
            Regex("(?:por|o/|valor\\s+medio\\s+por)?\\s*100\\s*(?:g|ml|9)\\b"),
            Regex("\\b100\\s*(?:g|ml|9)\\b")
        )
        val secondColPattern = Regex("(?:\\||%|porc|rac|dosis|unid|\\b\\d{1,2}\\s*(?:g|ml)\\b)")
        for (pattern in patterns) {
            val match = lines.firstOrNull { pattern.containsMatchIn(it.normalizedText) }
            if (match != null) {
                val box = match.box
                val text = match.normalizedText

                val idx100 = text.indexOf("100")
                if (idx100 >= 0) {
                    val after100 = text.substring(idx100 + 3)
                    val secondColMatch = secondColPattern.find(after100)
                    if (secondColMatch != null) {
                        val splitIdx = idx100 + 3 + secondColMatch.range.first
                        val fraction = splitIdx.toDouble() / text.length
                        val right = box.left + ((box.right - box.left) * fraction).toInt()
                        return OcrBox(box.left, box.top, right, box.bottom)
                    }
                }
                return box
            }
        }
        return null
    }

    // Step 4: Locate portion column boxes in header zone only
    private fun findPortionColumnBoxes(lines: List<NormalizedLine>, col100gBox: OcrBox): List<OcrBox> {
        val headerYMargin = max(col100gBox.bottom - col100gBox.top, 100)
        val headerTop = col100gBox.top - headerYMargin
        val headerBottom = col100gBox.bottom + headerYMargin

        val portionPattern = Regex("(?:porci[oó]n|raci[oó]n|dosis|unidad|\\b\\d{1,2}(?:[\\.,]\\d+)?\\s*(?:g|ml)\\b|%\\s*r?i|%\\s*vr|vrn)")
        val header100Pattern = Regex("100\\s*(?:g|ml|9)")

        val result = mutableListOf<OcrBox>()

        val multiHeader = lines.firstOrNull { line ->
            line.box.top in headerTop..headerBottom &&
                header100Pattern.containsMatchIn(line.normalizedText) &&
                line.box.right > col100gBox.right + 40
        }
        if (multiHeader != null) {
            result.add(OcrBox(col100gBox.right + 10, multiHeader.box.top, multiHeader.box.right, multiHeader.box.bottom))
        }

        val separatePortions = lines.filter { line ->
            line.box.top in headerTop..headerBottom &&
                line.box.left >= col100gBox.left + 40 &&
                portionPattern.containsMatchIn(line.normalizedText) &&
                !header100Pattern.containsMatchIn(line.normalizedText)
        }.map { it.box }

        result.addAll(separatePortions)
        return result
    }

    // Step 2: Locate rows by keyword
    private fun locateNutrientRows(lines: List<NormalizedLine>, col100gBox: OcrBox?): Map<NutrientType, NormalizedLine> {
        val result = mutableMapOf<NutrientType, NormalizedLine>()

        val minTableTop = if (col100gBox != null) col100gBox.top - 150 else 0

        val sortedLines = lines.sortedBy { it.box.top }

        for (i in sortedLines.indices) {
            val line = sortedLines[i]
            val text = line.normalizedText

            if (col100gBox != null) {
                if (line.box.top < minTableTop) continue
                // Keyword right edge must be reasonably near 100g column left edge
                if (line.box.right < col100gBox.left - 900 || line.box.left > col100gBox.left + 50) continue
            }

            if (isExcludedHeaderLine(text) || isIngredientParagraph(line)) continue

            val matchedType: NutrientType? = when {
                isSatFatKeyword(text) -> NutrientType.SAT_FAT
                isFatKeyword(text) -> NutrientType.FAT
                isSugarsKeyword(text) -> NutrientType.SUGARS
                isCarbsKeyword(text) -> NutrientType.CARBS
                isEnergyKeyword(text) -> NutrientType.ENERGY
                isFiberKeyword(text) -> NutrientType.FIBER
                isProteinKeyword(text) -> NutrientType.PROTEIN
                isSaltKeyword(text) -> NutrientType.SALT
                else -> null
            }

            if (matchedType != null && !result.containsKey(matchedType)) {
                var mergedBox = line.box
                var mergedText = line.normalizedText

                val lineH = max(line.box.bottom - line.box.top, 1)
                val continuationCandidate = lines.firstOrNull { other ->
                    other != line &&
                        abs(other.box.left - line.box.left) < 180 &&
                        other.box.top >= line.box.top &&
                        other.box.top <= line.box.bottom + lineH * 1.0 &&
                        isContinuationLine(matchedType, other.normalizedText)
                }

                if (continuationCandidate != null) {
                    mergedBox = OcrBox(
                        left = min(line.box.left, continuationCandidate.box.left),
                        top = min(line.box.top, continuationCandidate.box.top),
                        right = max(line.box.right, continuationCandidate.box.right),
                        bottom = max(line.box.bottom, continuationCandidate.box.bottom)
                    )
                    mergedText = "$mergedText ${continuationCandidate.normalizedText}"
                }

                result[matchedType] = NormalizedLine(
                    original = line.original,
                    normalizedText = mergedText,
                    box = mergedBox
                )
            }
        }

        return result
    }

    private fun isIngredientParagraph(line: NormalizedLine): Boolean {
        val text = line.normalizedText
        if (text.endsWith("grasas/") || text.endsWith("grasas") ||
            text.endsWith(".grasas") || text.endsWith("lipidos") ||
            text.endsWith("energia") || text.endsWith("carbono") ||
            text.endsWith("proteinas")
        ) {
            return false
        }
        // General ingredient list markers: comma-separated lists, food additive E-numbers, or percentages with commas
        if (text.count { it == ',' } >= 2) return true
        if (Regex("\\be-?\\d{3}[a-z]?\\b").containsMatchIn(text)) return true
        if (text.contains(",") && text.contains("%")) return true
        if (text.contains("fibra de ")) return true
        return false
    }

    private fun isContinuationLine(type: NutrientType, text: String): Boolean {
        return when (type) {
            NutrientType.ENERGY -> text.contains("energia") || text.contains("energetico") || text.contains("energy")
            NutrientType.CARBS -> text.contains("carbono") || text.contains("arbono")
            NutrientType.FAT -> text.contains("lipido") || text.contains("gordura")
            NutrientType.SAT_FAT -> text.contains("saturad")
            NutrientType.SUGARS -> text.contains("azucar") || text.contains("acucar")
            NutrientType.FIBER -> text.contains("fibra")
            NutrientType.PROTEIN -> text.contains("proteina")
            NutrientType.SALT -> text.contains("sal")
        }
    }

    private fun isExcludedHeaderLine(text: String): Boolean {
        if (text.contains("alto en") || text.contains("alto %") || text.contains("bajo en")) return true
        if (text.contains("en grasa") || text.contains("en proteinas") || text.contains("con proteinas")) return true
        if (text.contains("proteinas de") || text.contains("proteinasde") || text.contains("proteina de")) return true
        if (text.contains("ingredientes") || text.contains("conservar") || text.contains("fabricado por")) return true
        if (text.contains("con sal") || text.contains("sin sal")) return true
        if (text.startsWith("valor medio por 100")) return true
        return false
    }

    private fun isEnergyKeyword(text: String): Boolean {
        if (text.startsWith("valor medio por 100")) return false
        if (text.contains("menos de grasa") || text.contains("menos gordura")) return false
        return (text.contains("energ") ||
            text.trim() == "valor" ||
            (text.contains("kj") && (text.contains("kcal") || text.contains("keal"))))
    }

    private fun isFatKeyword(text: String): Boolean {
        if (text.contains("saturad") || text.contains("de las cuales") || text.contains("dos quais")) return false
        if (text.contains("menos de grasa") || text.contains("en grasa")) return false
        return text.contains("grasa") || text.contains("lipido") || text.contains("gordura") ||
            Regex("\\bfat\\b").containsMatchIn(text)
    }

    private fun isSatFatKeyword(text: String): Boolean {
        return text.contains("saturad") || text.contains("acidos grasos saturados")
    }

    private fun isCarbsKeyword(text: String): Boolean {
        if (text.contains("azucar") || text.contains("acucar")) return false
        return text.contains("hidrato") || text.contains("carbono") || text.contains("arbono") ||
            text.contains("midrato") || text.contains("carbohidrato") || text.contains("carboidrato")
    }

    private fun isSugarsKeyword(text: String): Boolean {
        return text.contains("azucar") || text.contains("acucar") || text.contains("sugars")
    }

    private fun isFiberKeyword(text: String): Boolean {
        if (text.contains("fibra de ")) return false
        return text.contains("fibra")
    }

    private fun isProteinKeyword(text: String): Boolean {
        if (text.contains("con proteinas") || text.contains("alto %") || text.contains("alto en") || text.contains("en proteinas")) return false
        return text.contains("proteina") || text.contains("proteins")
    }

    private fun isSaltKeyword(text: String): Boolean {
        if (text.contains("ingrediente") || text.contains("con sal") || text.contains("sin sal")) return false
        if (text.contains(",")) return false
        val clean = text.replace(Regex("[:|/\\-]"), " ").trim()
        val tokens = clean.split(Regex("\\s+"))
        return tokens.any { it == "sal" || it == "salt" || it == "sa" }
    }

    private fun isAnyKeywordLine(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("energ") || lower.contains("grasa") || lower.contains("lipido") ||
            lower.contains("saturad") || lower.contains("hidrato") || lower.contains("carbono") ||
            lower.contains("azucar") || lower.contains("proteina") || isSaltKeyword(text) ||
            lower.contains("fibra") || lower.contains("valor medio")
    }

    // Step 3 & 5 & 6: Extract nutrient value from inline text or vertically aligned lines
    private fun extractNutrientValue(
        type: NutrientType,
        keywordLine: NormalizedLine,
        allLines: List<NormalizedLine>,
        col100gBox: OcrBox?,
        portionBoxes: List<OcrBox>,
        usedLines: MutableSet<NormalizedLine>
    ): ParsedValue? {
        if (type == NutrientType.ENERGY) {
            return extractEnergyValue(keywordLine, allLines, col100gBox, portionBoxes, usedLines)
        }

        // 1. Try inline extraction
        val inlineValue = extractInlineNutrientNumber(keywordLine.normalizedText)
        if (inlineValue != null) {
            return inlineValue
        }

        // 2. Find row candidates
        val candidates = findRowCandidates(type, keywordLine, allLines, col100gBox, portionBoxes, usedLines)
        for (cand in candidates) {
            val parsed = extractNumberFromText(cand.normalizedText)
            if (parsed != null) {
                usedLines.add(cand)
                return parsed
            }
        }

        return null
    }

    // Step 5: Energy parsing (kcal vs kJ)
    private fun extractEnergyValue(
        keywordLine: NormalizedLine,
        allLines: List<NormalizedLine>,
        col100gBox: OcrBox?,
        portionBoxes: List<OcrBox>,
        usedLines: MutableSet<NormalizedLine>
    ): ParsedValue? {
        val kBox = keywordLine.box
        val expandedEnergyLine = keywordLine.copy(
            box = OcrBox(kBox.left, kBox.top, kBox.right, kBox.bottom + (kBox.bottom - kBox.top) * 2)
        )
        val candidates = findRowCandidates(NutrientType.ENERGY, expandedEnergyLine, allLines, col100gBox, portionBoxes, usedLines)

        // 1. Explicit kcal in inline text
        val inlineKcal = parseExplicitKcal(keywordLine.normalizedText)
        if (inlineKcal != null) return inlineKcal

        // 2. Explicit kcal in row candidates (e.g. "327 kcal" or "712 kcal" or "734 kcal")
        for (cand in candidates) {
            val parsedKcal = parseExplicitKcal(cand.normalizedText)
            if (parsedKcal != null) {
                usedLines.add(cand)
                return parsedKcal
            }
        }

        // 3. Fallback to kJ conversion from inline
        val inlineKj = parseKjConversion(keywordLine.normalizedText)
        if (inlineKj != null) return inlineKj

        // 4. Fallback to kJ conversion from candidate
        for (cand in candidates) {
            val parsedKj = parseKjConversion(cand.normalizedText)
            if (parsedKj != null) {
                usedLines.add(cand)
                return parsedKj
            }
        }

        return null
    }

    private fun parseExplicitKcal(text: String): ParsedValue? {
        val clean = text.replace("keal", "kcal").replace("kca1", "kcal")

        val kcalPattern = Regex("(\\d+(?:[\\.,]\\d+)?)\\s*[l|i]?\\s*kcal")
        val kcalMatch = kcalPattern.find(clean)
        if (kcalMatch != null) {
            val numStr = cleanNumberString(kcalMatch.groupValues[1])
            val num = numStr.toDoubleOrNull()
            if (num != null) {
                return ParsedValue(value = round(num), isApproximate = false, fromKj = false, rawToken = kcalMatch.groupValues[1])
            }
        }

        val simpleKcal = Regex("\\b(\\d{2,4})\\s*kcal").find(clean)
        if (simpleKcal != null) {
            val num = simpleKcal.groupValues[1].toDoubleOrNull()
            if (num != null) {
                return ParsedValue(value = round(num), isApproximate = false, fromKj = false, rawToken = simpleKcal.groupValues[1])
            }
        }

        return null
    }

    private fun parseKjConversion(text: String): ParsedValue? {
        val clean = text.replace("keal", "kcal").replace("kca1", "kcal")
        val kjPattern = Regex("(\\d{2,5}(?:[\\.,]\\d+)?)\\s*kj")
        val kjMatch = kjPattern.find(clean)
        if (kjMatch != null) {
            val numStr = cleanNumberString(kjMatch.groupValues[1])
            val kj = numStr.toDoubleOrNull()
            if (kj != null) {
                val kcal = round(kj / 4.184)
                return ParsedValue(value = kcal, isApproximate = false, fromKj = true, rawToken = kjMatch.groupValues[1])
            }
        }
        return null
    }

    // Step 3: Find lines on the same row using bounding box vertical overlap
    private fun findRowCandidates(
        type: NutrientType,
        keywordLine: NormalizedLine,
        allLines: List<NormalizedLine>,
        col100gBox: OcrBox?,
        portionBoxes: List<OcrBox>,
        usedLines: Set<NormalizedLine>
    ): List<NormalizedLine> {
        val kBox = keywordLine.box
        val kHeight = max(1, kBox.bottom - kBox.top)
        val kCenterY = (kBox.top + kBox.bottom) / 2

        val rowLines = allLines.filter { cand ->
            if (cand == keywordLine || cand in usedLines) return@filter false
            val cBox = cand.box

            // Discard other keyword label lines from being numeric candidates
            if (isAnyKeywordLine(cand.normalizedText)) return@filter false

            // Non-energy nutrients cannot take energy unit lines (kcal/kj)
            if (type != NutrientType.ENERGY && (cand.normalizedText.contains("kcal") || cand.normalizedText.contains("kj"))) {
                return@filter false
            }

            // Candidate must be to the right of keyword text start
            if (cBox.left < kBox.left + 40) return@filter false

            val cHeight = max(1, cBox.bottom - cBox.top)
            if (cHeight > kHeight * 2.5) return@filter false

            val overlap = min(kBox.bottom, cBox.bottom) - max(kBox.top, cBox.top)
            val minH = min(kHeight, cHeight)
            val hasOverlap = overlap > 0 && (overlap.toDouble() / minH >= 0.18)
            val centerDiff = abs((kBox.top + kBox.bottom) / 2 - (cBox.top + cBox.bottom) / 2)
            val isAligned = hasOverlap || (centerDiff <= max(kHeight, cHeight) * 0.65)

            isAligned && hasNumericContent(cand.normalizedText)
        }

        if (rowLines.isEmpty()) return emptyList()

        return if (col100gBox != null) {
            val colCenter = (col100gBox.left + col100gBox.right) / 2
            val maxHDist = max(col100gBox.right - col100gBox.left, 160) * 1.5

            val nonPortionLines = rowLines.filter { cand ->
                val candCenter = (cand.box.left + cand.box.right) / 2
                val inPortion = portionBoxes.any { pBox ->
                    val pCenter = (pBox.left + pBox.right) / 2
                    abs(candCenter - pCenter) < abs(candCenter - colCenter) && candCenter > colCenter + 50
                }
                !inPortion && abs(candCenter - colCenter) <= maxHDist
            }

            val targetList = if (nonPortionLines.isNotEmpty()) nonPortionLines else rowLines
            targetList.sortedWith(
                compareBy(
                    // 1. Must be in the 100g column (distance < 120 vs far portion)
                    { cand ->
                        val candCenter = (cand.box.left + cand.box.right) / 2
                        val hDist = abs(candCenter - colCenter)
                        if (hDist < 120) 0 else 1
                    },
                    // 2. Nearest vertical center to the row
                    { cand ->
                        val candCenterY = (cand.box.top + cand.box.bottom) / 2
                        abs(candCenterY - kCenterY)
                    },
                    // 3. Horizontal distance
                    { cand ->
                        val candCenter = (cand.box.left + cand.box.right) / 2
                        abs(candCenter - colCenter)
                    }
                )
            )
        } else {
            rowLines.sortedWith(
                compareBy(
                    { cand ->
                        val candCenterY = (cand.box.top + cand.box.bottom) / 2
                        abs(candCenterY - kCenterY)
                    },
                    { it.box.left }
                )
            )
        }
    }

    private fun hasNumericContent(text: String): Boolean {
        if (text.contains("trazas") || text.contains("tracos")) return true
        return Regex("\\d").containsMatchIn(text)
    }

    private fun extractInlineNutrientNumber(text: String): ParsedValue? {
        val trimmed = text.trim()
        if (Regex("(?:[:|\\-]?\\s*|\\s+)(?:trazas|tracos)$").containsMatchIn(trimmed)) {
            return ParsedValue(value = 0.0, isApproximate = true, rawToken = "trazas")
        }
        val pattern = Regex("(?:[:|\\-]?\\s*|de\\s+)?(<?\\s*\\d+(?:[\\.,]\\d+)?)\\s*(?:g|9|mg)?$")
        val match = pattern.find(trimmed)
        if (match != null) {
            val rawStr = match.groupValues[1]
            return parseNumberString(rawStr)
        }
        return null
    }

    private fun extractNumberFromText(text: String): ParsedValue? {
        val clean = text.trim()
        if (clean.contains("trazas") || clean.contains("tracos")) {
            return ParsedValue(value = 0.0, isApproximate = true, rawToken = "trazas")
        }

        val pattern = Regex("(<?\\s*\\d+(?:[\\.,]\\d+)?)\\s*(?:g|9|mg)?")
        val match = pattern.find(clean)
        if (match != null) {
            val raw = match.groupValues[1]
            return parseNumberString(raw)
        }

        return null
    }

    private fun parseNumberString(raw: String): ParsedValue? {
        val isApprox = raw.contains("<")
        var s = raw.replace("<", "").trim()
        s = cleanNumberString(s)

        val num = s.toDoubleOrNull() ?: return null
        val finalNum = if (isApprox && num <= 0.5) 0.5 else num
        return ParsedValue(value = finalNum, isApproximate = isApprox, rawToken = s)
    }

    private fun cleanNumberString(raw: String): String {
        var s = raw.replace(',', '.')
        s = s.replace('o', '0').replace('O', '0')
        s = s.replace('l', '1').replace('I', '1').replace('|', '1')
        s = s.replace('s', '5').replace('S', '5')
        s = s.replace('b', '8').replace('B', '8')

        if (Regex("^\\d+\\.\\d9$").matches(s)) {
            s = s.substring(0, s.length - 1)
        }

        return s
    }

    // Step 1: Handle OCR missing decimal commas and trailing '9'-as-'g' artifacts
    private fun refineDecimalValues(extracted: MutableMap<NutrientType, ParsedValue>) {
        for ((type, parsed) in extracted.entries) {
            if (type == NutrientType.ENERGY) continue

            var v = parsed.value
            val raw = parsed.rawToken
            var inferred = false

            if (raw.startsWith("0") && raw.length == 3 && !raw.contains(".") && !raw.contains(",")) {
                v = if (raw.endsWith("9")) {
                    raw.substring(0, 2).toDouble() / 10.0
                } else {
                    raw.toDouble() / 100.0
                }
                inferred = true
            } else if (raw.length in 3..4 && raw.endsWith("9") && !raw.contains(".") && !raw.contains(",")) {
                // Trailing '9' is OCR misread of unit 'g' (e.g. "959" -> "95g", "1369" -> "136g")
                v = raw.substring(0, raw.length - 1).toDouble()
                inferred = true
            }

            while (v > 100.0) {
                v /= 10.0
                inferred = true
            }

            if (type == NutrientType.SALT && v > 15.0) {
                v /= 10.0
                inferred = true
            }

            if (v != parsed.value) {
                extracted[type] = parsed.copy(
                    value = round(v * 100.0) / 100.0,
                    inferredDecimal = inferred
                )
            }
        }

        // Check if 2-digit numbers missed their decimal separator (e.g., "14g" for "1,4 g")
        val kcal = extracted[NutrientType.ENERGY]?.value
        if (kcal != null && kcal > 0) {
            val pParsed = extracted[NutrientType.PROTEIN]
            val cParsed = extracted[NutrientType.CARBS]
            val fParsed = extracted[NutrientType.FAT]
            val fiber = extracted[NutrientType.FIBER]?.value ?: 0.0

            val pOrig = pParsed?.value ?: 0.0
            val cOrig = cParsed?.value ?: 0.0
            val fOrig = fParsed?.value ?: 0.0

            val initialDiff = abs(4.0 * pOrig + 4.0 * cOrig + 9.0 * fOrig + 2.0 * fiber - kcal)

            if (initialDiff > 0.15 * kcal) {
                var bestDiff = initialDiff
                var bestP = pOrig
                var bestC = cOrig
                var bestF = fOrig

                val pCanScale = pParsed != null && !pParsed.rawToken.contains(".") && !pParsed.rawToken.contains(",") && pOrig >= 10.0
                val cCanScale = cParsed != null && !cParsed.rawToken.contains(".") && !cParsed.rawToken.contains(",") && cOrig >= 10.0
                val fCanScale = fParsed != null && !fParsed.rawToken.contains(".") && !fParsed.rawToken.contains(",") && fOrig >= 10.0

                val pOptions = if (pCanScale) listOf(pOrig, round((pOrig / 10.0) * 10.0) / 10.0) else listOf(pOrig)
                val cOptions = if (cCanScale) listOf(cOrig, round((cOrig / 10.0) * 10.0) / 10.0) else listOf(cOrig)
                val fOptions = if (fCanScale) listOf(fOrig, round((fOrig / 10.0) * 10.0) / 10.0) else listOf(fOrig)

                for (p in pOptions) {
                    for (c in cOptions) {
                        for (f in fOptions) {
                            val diff = abs(4.0 * p + 4.0 * c + 9.0 * f + 2.0 * fiber - kcal)
                            if (diff < bestDiff && diff <= 0.15 * kcal) {
                                bestDiff = diff
                                bestP = p
                                bestC = c
                                bestF = f
                            }
                        }
                    }
                }

                if (bestP != pOrig && extracted.containsKey(NutrientType.PROTEIN)) {
                    extracted[NutrientType.PROTEIN] = extracted[NutrientType.PROTEIN]!!.copy(value = bestP, inferredDecimal = true)
                }
                if (bestC != cOrig && extracted.containsKey(NutrientType.CARBS)) {
                    extracted[NutrientType.CARBS] = extracted[NutrientType.CARBS]!!.copy(value = bestC, inferredDecimal = true)
                }
                if (bestF != fOrig && extracted.containsKey(NutrientType.FAT)) {
                    extracted[NutrientType.FAT] = extracted[NutrientType.FAT]!!.copy(value = bestF, inferredDecimal = true)
                }
            }
        }

        // Sub-macro missing decimal separator check: sugars <= carbs, satFat <= fat
        val carbs = extracted[NutrientType.CARBS]?.value
        val sugarsParsed = extracted[NutrientType.SUGARS]
        if (carbs != null && sugarsParsed != null &&
            !sugarsParsed.rawToken.contains(".") && !sugarsParsed.rawToken.contains(",") &&
            sugarsParsed.value > carbs + 0.1
        ) {
            val fixedSugars = sugarsParsed.value / 10.0
            if (fixedSugars <= carbs + 0.1) {
                extracted[NutrientType.SUGARS] = sugarsParsed.copy(
                    value = round(fixedSugars * 100.0) / 100.0,
                    inferredDecimal = true
                )
            }
        }

        val fat = extracted[NutrientType.FAT]?.value
        val satFatParsed = extracted[NutrientType.SAT_FAT]
        if (fat != null && satFatParsed != null &&
            !satFatParsed.rawToken.contains(".") && !satFatParsed.rawToken.contains(",") &&
            satFatParsed.value > fat + 0.1
        ) {
            val fixedSatFat = satFatParsed.value / 10.0
            if (fixedSatFat <= fat + 0.1) {
                extracted[NutrientType.SAT_FAT] = satFatParsed.copy(
                    value = round(fixedSatFat * 100.0) / 100.0,
                    inferredDecimal = true
                )
            }
        }
    }
}
