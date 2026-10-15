package com.controlcal.core.solver

import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.Product
import kotlin.math.abs
import kotlin.math.max

/**
 * Solves the bounded quadratic programming problem for Macro Tetris:
 * Minimizes sum(w_i * ((A*x - b)_i / max(b_i, 1.0))^2)
 * subject to min_g <= x_j <= max_g for each product.
 *
 * Implemented in pure Kotlin without external numerical libraries,
 * using Gauss-Seidel Coordinate Descent on box constraints.
 * Conforms to PLAN.md section 5.6 (steps 1, 2, 3).
 */
object MacroSolver {

    /**
     * Resolves the effective [minG, maxG] bounds for a product.
     * PLAN.md 5.6 step 2:
     * - minG defaults to 0.0 if null.
     * - maxG defaults to 30.0 g if fat100g > 80.0, otherwise 300.0 g.
     */
    internal fun resolveBounds(product: Product): Pair<Double, Double> {
        val minG = (product.minG ?: 0.0).coerceAtLeast(0.0)
        val defaultMax = if (product.fat100g > 80.0) 30.0 else 300.0
        val maxG = (product.maxG ?: defaultMax).coerceAtLeast(minG)
        return Pair(minG, maxG)
    }

    /**
     * Solves the continuous portion allocation (in grams) for the given [products].
     * Returns a map associating each product with its optimal grams (continuous, not rounded).
     */
    fun solve(
        target: MacroTargets,
        products: List<Product>,
        weights: MacroWeights = MacroWeights(),
    ): Map<Product, Double> {
        if (products.isEmpty()) return emptyMap()

        // If all target macros are <= 0, no food should be added (target met or surplus)
        if (target.kcal <= 0.0 && target.protein <= 0.0 && target.carbs <= 0.0 && target.fat <= 0.0) {
            return products.associateWith { resolveBounds(it).first }
        }

        val k = products.size
        val b = doubleArrayOf(target.kcal, target.protein, target.carbs, target.fat)
        val s = doubleArrayOf(
            max(b[0], 1.0),
            max(b[1], 1.0),
            max(b[2], 1.0),
            max(b[3], 1.0),
        )
        val w = doubleArrayOf(weights.kcal, weights.protein, weights.carbs, weights.fat)
        val d = doubleArrayOf(
            w[0] / (s[0] * s[0]),
            w[1] / (s[1] * s[1]),
            w[2] / (s[2] * s[2]),
            w[3] / (s[3] * s[3]),
        )

        // Matrix A (4 rows x k columns): macros per gram
        val a = Array(4) { DoubleArray(k) }
        val lowerBounds = DoubleArray(k)
        val upperBounds = DoubleArray(k)

        for (j in 0 until k) {
            val p = products[j]
            a[0][j] = p.kcal100g / 100.0
            a[1][j] = p.protein100g / 100.0
            a[2][j] = p.carbs100g / 100.0
            a[3][j] = p.fat100g / 100.0

            val (l, u) = resolveBounds(p)
            lowerBounds[j] = l
            upperBounds[j] = u
        }

        // Precompute diagonal elements Q_jj = sum_{i=0}^3 d_i * a_{i,j}^2
        val qDiag = DoubleArray(k)
        for (j in 0 until k) {
            var sum = 0.0
            for (i in 0 until 4) {
                sum += d[i] * a[i][j] * a[i][j]
            }
            qDiag[j] = sum
        }

        // Initial point: start at lower bounds
        val x = DoubleArray(k) { lowerBounds[it] }

        // Initial residuals r_i = (Ax)_i - b_i
        val r = DoubleArray(4)
        for (i in 0 until 4) {
            var ax = 0.0
            for (j in 0 until k) {
                ax += a[i][j] * x[j]
            }
            r[i] = ax - b[i]
        }

        // Gauss-Seidel Coordinate Descent on box constraints [lowerBounds, upperBounds]
        val maxIterations = 80
        for (iter in 0 until maxIterations) {
            var maxChange = 0.0
            for (j in 0 until k) {
                val qjj = qDiag[j]
                if (qjj < 1e-12) continue

                // Gradient component g_j = sum_{i=0}^3 d_i * a_{i,j} * r_i
                var gj = 0.0
                for (i in 0 until 4) {
                    gj += d[i] * a[i][j] * r[i]
                }

                // 1D unconstrained step
                val unconstrainedXj = x[j] - (gj / qjj)

                // Project onto [lowerBounds[j], upperBounds[j]]
                val projectedXj = unconstrainedXj.coerceIn(lowerBounds[j], upperBounds[j])
                val delta = projectedXj - x[j]

                if (abs(delta) > 1e-9) {
                    // Update residuals r_i += a_{i,j} * delta
                    for (i in 0 until 4) {
                        r[i] += a[i][j] * delta
                    }
                    x[j] = projectedXj
                    if (abs(delta) > maxChange) {
                        maxChange = abs(delta)
                    }
                }
            }
            if (maxChange < 1e-6) break
        }

        val result = LinkedHashMap<Product, Double>(k)
        for (j in 0 until k) {
            result[products[j]] = x[j]
        }
        return result
    }

    /**
     * Calculates the normalized weighted quadratic loss:
     * sum_{i=0}^3 w_i * ((Ax - b)_i / max(b_i, 1.0))^2
     */
    fun calculateLoss(
        target: MacroTargets,
        amounts: Map<Product, Double>,
        weights: MacroWeights = MacroWeights(),
    ): Double {
        var achievedKcal = 0.0
        var achievedProtein = 0.0
        var achievedCarbs = 0.0
        var achievedFat = 0.0

        for ((product, grams) in amounts) {
            val factor = grams / 100.0
            achievedKcal += product.kcal100g * factor
            achievedProtein += product.protein100g * factor
            achievedCarbs += product.carbs100g * factor
            achievedFat += product.fat100g * factor
        }

        val sKcal = max(target.kcal, 1.0)
        val sProtein = max(target.protein, 1.0)
        val sCarbs = max(target.carbs, 1.0)
        val sFat = max(target.fat, 1.0)

        val errKcal = (achievedKcal - target.kcal) / sKcal
        val errProtein = (achievedProtein - target.protein) / sProtein
        val errCarbs = (achievedCarbs - target.carbs) / sCarbs
        val errFat = (achievedFat - target.fat) / sFat

        return (weights.kcal * errKcal * errKcal) +
            (weights.protein * errProtein * errProtein) +
            (weights.carbs * errCarbs * errCarbs) +
            (weights.fat * errFat * errFat)
    }
}
