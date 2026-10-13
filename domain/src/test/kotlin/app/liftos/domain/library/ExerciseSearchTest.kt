package app.liftos.domain.library

import app.liftos.domain.TestData
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchTest {
    private val all = TestData.library.exercises

    @Test
    fun `empty filter returns every non-archived exercise`() {
        assertEquals(all.size, ExerciseSearch.apply(all, ExerciseFilter()).size)
    }

    @Test
    fun `search by english name is case-insensitive`() {
        val r = ExerciseSearch.apply(all, ExerciseFilter(query = "bench PRESS"))
        assertTrue(r.any { it.id == "barbell-bench-press" })
        assertTrue(r.all { it.nameEn.lowercase().contains("bench") && it.nameEn.lowercase().contains("press") })
    }

    @Test
    fun `search by hebrew name`() {
        val r = ExerciseSearch.apply(all, ExerciseFilter(query = "סקוואט"))
        assertTrue(r.any { it.id == "barbell-back-squat" })
        assertTrue(r.any { it.id == "hack-squat" })
    }

    @Test
    fun `exact and prefix matches rank first`() {
        val r = ExerciseSearch.apply(all, ExerciseFilter(query = "plank"))
        assertEquals("plank", r.first().id)
    }

    @Test
    fun `filter by muscle includes secondary muscles`() {
        val chest = ExerciseSearch.apply(all, ExerciseFilter(muscle = MuscleGroup.CHEST))
        assertTrue(chest.any { it.id == "barbell-bench-press" })
        assertTrue(chest.any { it.id == "overhead-press" || it.id == "landmine-press" }) // chest is a secondary muscle there
    }

    @Test
    fun `filters combine`() {
        val r = ExerciseSearch.apply(
            all,
            ExerciseFilter(muscle = MuscleGroup.BICEPS, equipment = Equipment.DUMBBELL, type = ExerciseType.ISOLATION),
        )
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.equipment == Equipment.DUMBBELL && it.type == ExerciseType.ISOLATION })
    }

    @Test
    fun `archived and builtin visibility`() {
        val archived = all.first().copy(id = "x", isArchived = true, nameEn = "Zzz", nameHe = "זזז")
        val custom = all.first().copy(id = "c", isBuiltIn = false, nameEn = "Mine", nameHe = "שלי")
        val list = all + archived + custom
        assertFalse(ExerciseSearch.apply(list, ExerciseFilter()).any { it.id == "x" })
        assertTrue(ExerciseSearch.apply(list, ExerciseFilter(includeArchived = true)).any { it.id == "x" })
        assertEquals(listOf("c"), ExerciseSearch.apply(list, ExerciseFilter(onlyCustom = true)).map { it.id })
    }

    @Test
    fun `normalization strips niqqud and punctuation`() {
        assertEquals("לחיצת חזה", ExerciseSearch.normalize("  לַחִיצַת   חזה "))
        assertEquals("t bar row", ExerciseSearch.normalize("T-Bar Row"))
    }
}
