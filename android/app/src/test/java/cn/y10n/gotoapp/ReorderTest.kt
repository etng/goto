package cn.y10n.gotoapp

import cn.y10n.gotoapp.data.Reorder
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {

    @Test
    fun move_down() {
        assertEquals(
            listOf("a", "c", "b", "d"),
            Reorder.move(listOf("a", "b", "c", "d"), from = 1, to = 2),
        )
    }

    @Test
    fun move_up() {
        assertEquals(
            listOf("b", "a", "c"),
            Reorder.move(listOf("a", "b", "c"), from = 1, to = 0),
        )
    }

    @Test
    fun move_sameIndex_noop() {
        val l = listOf("a", "b", "c")
        assertEquals(l, Reorder.move(l, 1, 1))
    }

    @Test
    fun move_outOfBounds_noop() {
        val l = listOf("a", "b")
        assertEquals(l, Reorder.move(l, -1, 1))
        assertEquals(l, Reorder.move(l, 0, 5))
    }
}
