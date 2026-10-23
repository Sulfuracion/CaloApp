package com.controlcal.app.ui.confirm

import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.ocr.ValidationIssue
import com.controlcal.core.ocr.ValidationRule

internal fun ValidationIssue.toUiText(): UiText {
    val first = NumberFormatHelper.formatDecimal(values.getOrNull(0), maxDecimals = 1)
    val second = NumberFormatHelper.formatDecimal(values.getOrNull(1), maxDecimals = 1)
    return when (rule) {
        ValidationRule.NON_NEGATIVE -> UiText.StringResource(
            R.string.confirm_product_ocr_non_negative,
            first,
        )
        ValidationRule.KCAL_LEQ_900 -> UiText.StringResource(
            R.string.confirm_product_ocr_kcal_limit,
            first,
        )
        ValidationRule.VALUE_LEQ_100 -> UiText.StringResource(
            R.string.confirm_product_ocr_value_limit,
            first,
        )
        ValidationRule.SAT_FAT_LEQ_FAT -> UiText.StringResource(
            R.string.confirm_product_ocr_sat_fat_limit,
            first,
            second,
        )
        ValidationRule.SUGARS_LEQ_CARBS -> UiText.StringResource(
            R.string.confirm_product_ocr_sugars_limit,
            first,
            second,
        )
        ValidationRule.CALORIC_BALANCE -> UiText.StringResource(
            R.string.confirm_product_ocr_caloric_balance,
            first,
            second,
        )
    }
}
