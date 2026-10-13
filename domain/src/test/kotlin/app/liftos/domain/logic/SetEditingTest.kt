package app.liftos.domain.logic

import app.liftos.domain.SeqIds
import app.liftos.domain.TestData
import app.liftos.domain.TestData.set
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEditingTest {
    private val bench = TestData.sessionExercise()
    private val split = TestData.sessionExercise(id = "split", unilateral = true)

    @Test
    fun `add set copies the previous working set and is not completed`() {
        val existing = listOf(set("a", order = 0, weight = 80.0, reps = 6, rir = 2))
        val result = SetEditing.addSet(bench, existing, idGen = SeqIds())
        assertEquals(2, result.size)
        val added = result.last()
        assertEquals(80.0, added.weightKg!!, 0.0)
        assertEquals(6, added.reps)
        assertEquals(2, added.rir)
        assertFalse(added.isCompleted)
        assertEquals(listOf(0, 1), result.map { it.orderIndex })
    }

    @Test
    fun `warm-up is inserted after existing warm-ups and before working sets`() {
        val existing = listOf(
            set("w1", order = 0, type = SetType.WARMUP, weight = 40.0),
            set("a", order = 1, weight = 80.0),
            set("b", order = 2, weight = 80.0),
        )
        val result = SetEditing.addSet(bench, existing, SetType.WARMUP, SeqIds())
        assertEquals(listOf(SetType.WARMUP, SetType.WARMUP, SetType.WORKING, SetType.WORKING), result.map { it.setType })
        assertEquals(listOf(0, 1, 2, 3), result.map { it.orderIndex })
    }

    @Test
    fun `adding a set to a unilateral exercise adds a right and a left row`() {
        val result = SetEditing.addSet(split, emptyList(), idGen = SeqIds())
        assertEquals(listOf(Side.RIGHT, Side.LEFT), result.map { it.side })
    }

    @Test
    fun `deleting one side of a unilateral set removes the pair`() {
        var rows = SetEditing.addSet(split, emptyList(), idGen = SeqIds("a"))
        rows = SetEditing.addSet(split, rows, idGen = SeqIds("b"))
        assertEquals(4, rows.size)
        val firstRight = rows.first { it.side == Side.RIGHT }
        val after = SetEditing.deleteSet(split, rows, firstRight.id)
        assertEquals(2, after.size)
        assertEquals(listOf(0, 1), after.map { it.orderIndex })
    }

    @Test
    fun `deleting a normal set renumbers the rest`() {
        val rows = listOf(set("a", order = 0), set("b", order = 1), set("c", order = 2))
        val after = SetEditing.deleteSet(bench, rows, "b")
        assertEquals(listOf("a", "c"), after.map { it.id })
        assertEquals(listOf(0, 1), after.map { it.orderIndex })
    }

    @Test
    fun `labels number warm-ups and working sets separately`() {
        val rows = listOf(
            set("w1", order = 0, type = SetType.WARMUP),
            set("w2", order = 1, type = SetType.WARMUP),
            set("a", order = 2),
            set("b", order = 3),
        )
        val labels = SetEditing.labels(bench, rows)
        assertEquals("ח1", labels["w1"]!!.number)
        assertEquals("ח2", labels["w2"]!!.number)
        assertEquals("1", labels["a"]!!.number)
        assertEquals("2", labels["b"]!!.number)
    }

    @Test
    fun `unilateral labels share the number between sides`() {
        val rows = SetEditing.addSet(split, SetEditing.addSet(split, emptyList(), idGen = SeqIds("x")), idGen = SeqIds("y"))
        val labels = SetEditing.labels(split, rows)
        assertEquals(listOf("1", "1", "2", "2"), rows.map { labels[it.id]!!.number })
    }

    @Test
    fun `a set needs reps (or seconds for holds) before it can be completed`() {
        assertTrue(SetEditing.canComplete(bench, set("a", reps = 5)))
        assertFalse(SetEditing.canComplete(bench, set("b", reps = null)))
        assertFalse(SetEditing.canComplete(bench, set("c", reps = 0)))
        val plank = TestData.sessionExercise(tracking = TrackingType.DURATION)
        assertFalse(SetEditing.canComplete(plank, set("d", reps = null)))
        assertTrue(SetEditing.canComplete(plank, set("e", reps = null).copy(durationSec = 45)))
    }

    @Test
    fun `rest starts after the left side of a unilateral set, not between sides`() {
        val right = set("r").copy(side = Side.RIGHT)
        val left = set("l").copy(side = Side.LEFT)
        assertFalse(SetEditing.shouldStartRestAfter(split, right))
        assertTrue(SetEditing.shouldStartRestAfter(split, left))
        assertTrue(SetEditing.shouldStartRestAfter(bench, set("n")))
    }

    @Test
    fun `completion toggles the timestamp`() {
        val done = SetEditing.withCompletion(set("a", done = false), true, 123L)
        assertTrue(done.isCompleted)
        assertEquals(123L, done.completedAt)
        val undone = SetEditing.withCompletion(done, false, 456L)
        assertFalse(undone.isCompleted)
        assertNull(undone.completedAt)
    }
}
