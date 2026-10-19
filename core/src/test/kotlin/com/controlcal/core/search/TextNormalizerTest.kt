package com.controlcal.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun normalize_handlesVowelsWithAccents() {
        assertEquals("platano", TextNormalizer.normalize("Plátano"))
        assertEquals("arandanos", TextNormalizer.normalize("Arándanos"))
        assertEquals("cafe con leche", TextNormalizer.normalize("Café con leche"))
        assertEquals("solomillo iberico", TextNormalizer.normalize("Solomillo Ibérico"))
        assertEquals("melon de villaconejos", TextNormalizer.normalize("Melón de Villaconejos"))
        assertEquals("yogur de limon", TextNormalizer.normalize("Yogur de Limón"))
        assertEquals("atun claro", TextNormalizer.normalize("Atún claro"))
    }

    @Test
    fun normalize_handlesGraveAndCircumflexAccents() {
        assertEquals("creme brulee", TextNormalizer.normalize("Crème brûlée"))
        assertEquals("entrecote", TextNormalizer.normalize("Entrecôte"))
    }

    @Test
    fun normalize_handlesDieresis() {
        assertEquals("piraguismo", TextNormalizer.normalize("Piragüismo"))
        assertEquals("aguero", TextNormalizer.normalize("Agüero"))
        assertEquals("muesli", TextNormalizer.normalize("MÜESLI"))
    }

    @Test
    fun normalize_handlesEnye() {
        assertEquals("champinones", TextNormalizer.normalize("Champiñones"))
        assertEquals("lasana", TextNormalizer.normalize("Lasaña"))
        assertEquals("pinones", TextNormalizer.normalize("Piñones"))
        assertEquals("noquis de patata", TextNormalizer.normalize("Ñoquis de patata"))
        assertEquals("canas de lomo", TextNormalizer.normalize("CAÑAS DE LOMO"))
    }

    @Test
    fun normalize_handlesMultipleWhitespaceAndTrimming() {
        assertEquals("leche entera pascual", TextNormalizer.normalize("   Leche    entera   Pascual   "))
        assertEquals("pan integral", TextNormalizer.normalize("Pan\t\n  integral"))
    }

    @Test
    fun normalize_handlesEmptyAndNull() {
        assertEquals("", TextNormalizer.normalize(null))
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   "))
    }

    @Test
    fun normalize_handlesUppercaseAndMixedCase() {
        assertEquals("avena integral", TextNormalizer.normalize("AVENA INTEGRAL"))
        assertEquals("hacendado", TextNormalizer.normalize("HACENDADO"))
    }
}
