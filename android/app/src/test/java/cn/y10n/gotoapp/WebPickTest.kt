package cn.y10n.gotoapp

import cn.y10n.gotoapp.actions.OpenMode
import cn.y10n.gotoapp.actions.WebPick
import org.junit.Assert.assertEquals
import org.junit.Test

class WebPickTest {

    @Test
    fun singleFavoriteOpensDirectly() {
        val p = WebPick.decide(listOf("a", "b", "c"), listOf("b"))
        assertEquals(OpenMode.AUTO, p.mode)
        assertEquals(listOf("b"), p.candidates)
    }

    @Test
    fun multipleFavoritesShowChooserInFavoriteOrder() {
        val p = WebPick.decide(listOf("a", "b", "c"), listOf("c", "a"))
        assertEquals(OpenMode.CHOOSER, p.mode)
        assertEquals(listOf("c", "a"), p.candidates)
    }

    @Test
    fun noFavoriteFallsBackToSystemDefault() {
        val p = WebPick.decide(listOf("a", "b"), listOf("x"))
        assertEquals(OpenMode.DEFAULT, p.mode)
        assertEquals(listOf("a", "b"), p.candidates)
    }

    @Test
    fun favoriteThatCannotResolveIsIgnored() {
        val p = WebPick.decide(listOf("a", "b"), listOf("b", "c"))
        assertEquals(OpenMode.AUTO, p.mode)
        assertEquals(listOf("b"), p.candidates)
    }

    @Test
    fun emptyEverythingDefaults() {
        val p = WebPick.decide(emptyList(), emptyList())
        assertEquals(OpenMode.DEFAULT, p.mode)
    }
}
