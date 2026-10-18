package cn.y10n.gotoapp.data

/** 列表重排工具（拖放排序等场景，纯函数可单测） */
object Reorder {

    /** 把 from 位置的元素移到 to 位置（0-based）；越界/同位置原样返回 */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val l = list.toMutableList()
        val item = l.removeAt(from)
        l.add(to, item)
        return l
    }
}
