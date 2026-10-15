package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal

/**
 * Result of operations modifying the meal configuration.
 */
sealed interface MealConfigResult {
    data class Success(val meals: List<ConfiguredMeal>) : MealConfigResult

    sealed interface Failure : MealConfigResult {
        val message: String

        data class EmptyName(
            override val message: String = "El nombre de la comida no puede estar vacío",
        ) : Failure

        data class DuplicateName(
            override val message: String = "Ya existe una comida con ese nombre",
        ) : Failure

        data class ConflictingStartTime(
            override val message: String = "Dos comidas activas no pueden compartir la misma hora de inicio",
        ) : Failure

        data class AtLeastOneActiveRequired(
            override val message: String = "Debe quedar al menos una comida activa",
        ) : Failure

        data class MealHasEntriesCannotDelete(
            override val message: String = "Una comida con entradas registradas no se puede borrar, solo desactivar",
        ) : Failure

        data class MealNotFound(
            override val message: String = "Comida no encontrada",
        ) : Failure
    }
}
