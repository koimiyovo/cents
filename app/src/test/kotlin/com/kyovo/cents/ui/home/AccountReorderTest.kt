package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test

class RowTopsTest
{
    @Test
    fun `each row starts after the previous one and the gap`()
    {
        assertThat(rowTops(listOf(100f, 60f, 80f), spacing = 12f)).containsExactly(0f, 112f, 184f)
    }

    @Test
    fun `an empty list has no rows`()
    {
        assertThat(rowTops(emptyList(), spacing = 12f)).isEmpty()
    }
}

class ReorderTargetIndexTest
{
    // Three rows of 100 with no gap: centres at 50, 150 and 250.
    private val equal = listOf(100f, 100f, 100f)

    @Test
    fun `a row that has not moved stays where it is`()
    {
        assertThat(reorderTargetIndex(equal, 0f, from = 1, dragOffset = 0f)).isEqualTo(1)
    }

    @Test
    fun `dragging down goes after the next row once the dragged bottom edge passes its centre`()
    {
        // the first row's bottom edge starts at 100; the next centre is at 150
        assertThat(reorderTargetIndex(equal, 0f, from = 0, dragOffset = 49f)).isEqualTo(0)
        assertThat(reorderTargetIndex(equal, 0f, from = 0, dragOffset = 51f)).isEqualTo(1)
    }

    @Test
    fun `dragging up goes before the previous row once the dragged top edge passes its centre`()
    {
        // the last row's top edge starts at 200; the previous centre is at 150
        assertThat(reorderTargetIndex(equal, 0f, from = 2, dragOffset = -49f)).isEqualTo(2)
        assertThat(reorderTargetIndex(equal, 0f, from = 2, dragOffset = -51f)).isEqualTo(1)
    }

    @Test
    fun `a row dragged past the whole list ends first or last`()
    {
        assertThat(reorderTargetIndex(equal, 0f, from = 0, dragOffset = 10_000f)).isEqualTo(2)
        assertThat(reorderTargetIndex(equal, 0f, from = 2, dragOffset = -10_000f)).isEqualTo(0)
    }

    @Test
    fun `a row dragged as far as the list allows reaches the very end, however the heights compare`()
    {
        // GIVEN a first row as tall as, then taller than, the last one: its centre alone could
        // never pass the last row's centre once kept inside the list
        listOf(listOf(100f, 100f, 100f), listOf(200f, 60f, 60f), listOf(200f, 60f, 100f)).forEach { heights ->
            // WHEN dragged down to the limit, or the last row up to the limit
            val down = reorderTargetIndex(heights, 12f, from = 0, dragOffset = dragRange(heights, 12f, 0).endInclusive)
            val up = reorderTargetIndex(
                heights, 12f, from = heights.lastIndex, dragOffset = dragRange(heights, 12f, heights.lastIndex).start,
            )

            // THEN
            assertThat(down).isEqualTo(heights.lastIndex)
            assertThat(up).isEqualTo(0)
        }
    }

    @Test
    fun `rows of different heights are compared by their real edges and centres`()
    {
        // GIVEN a tall first row: tops 0, 212, 284; centres 100, 242, 314
        val heights = listOf(200f, 60f, 60f)

        // THEN the last row, dragged up, passes the middle one's centre (242) with its top edge
        // (starting at 284) after -42
        assertThat(reorderTargetIndex(heights, 12f, from = 2, dragOffset = -40f)).isEqualTo(2)
        assertThat(reorderTargetIndex(heights, 12f, from = 2, dragOffset = -45f)).isEqualTo(1)
        // and the tall first row, dragged down, passes it with its bottom edge (starting at 200)
        assertThat(reorderTargetIndex(heights, 12f, from = 0, dragOffset = 40f)).isEqualTo(0)
        assertThat(reorderTargetIndex(heights, 12f, from = 0, dragOffset = 45f)).isEqualTo(1)
    }

    @Test
    fun `the gap between rows counts`()
    {
        // GIVEN the second row's centre at 100 + 40 + 50 = 190, the first row's bottom edge at 100
        val heights = listOf(100f, 100f)

        // THEN
        assertThat(reorderTargetIndex(heights, 40f, from = 0, dragOffset = 89f)).isEqualTo(0)
        assertThat(reorderTargetIndex(heights, 40f, from = 0, dragOffset = 91f)).isEqualTo(1)
    }
}

class MoveItemTest
{
    @Test
    fun `moves an item down`()
    {
        assertThat(moveItem(listOf("a", "b", "c", "d"), from = 0, to = 2)).containsExactly("b", "c", "a", "d")
    }

    @Test
    fun `moves an item up`()
    {
        assertThat(moveItem(listOf("a", "b", "c", "d"), from = 3, to = 1)).containsExactly("a", "d", "b", "c")
    }

    @Test
    fun `moving an item onto itself changes nothing`()
    {
        assertThat(moveItem(listOf("a", "b", "c"), from = 1, to = 1)).containsExactly("a", "b", "c")
    }

    @Test
    fun `does not modify the list it is given`()
    {
        // GIVEN
        val original = listOf("a", "b", "c")

        // WHEN
        moveItem(original, from = 0, to = 2)

        // THEN
        assertThat(original).containsExactly("a", "b", "c")
    }
}

class ReorderShiftTest
{
    private val extent = 72f

    @Test
    fun `rows jumped over by a row dragged down step up by its extent`()
    {
        // GIVEN row 0 being dragged to position 2
        assertThat(reorderShift(index = 1, from = 0, to = 2, extent = extent)).isEqualTo(-extent)
        assertThat(reorderShift(index = 2, from = 0, to = 2, extent = extent)).isEqualTo(-extent)
    }

    @Test
    fun `rows jumped over by a row dragged up step down by its extent`()
    {
        // GIVEN row 3 being dragged to position 1
        assertThat(reorderShift(index = 1, from = 3, to = 1, extent = extent)).isEqualTo(extent)
        assertThat(reorderShift(index = 2, from = 3, to = 1, extent = extent)).isEqualTo(extent)
    }

    @Test
    fun `rows that are not jumped over, and the dragged row itself, do not step aside`()
    {
        assertThat(reorderShift(index = 0, from = 0, to = 2, extent = extent)).isZero()
        assertThat(reorderShift(index = 3, from = 0, to = 2, extent = extent)).isZero()
        assertThat(reorderShift(index = 0, from = 3, to = 1, extent = extent)).isZero()
        assertThat(reorderShift(index = 3, from = 3, to = 1, extent = extent)).isZero()
    }

    @Test
    fun `nothing steps aside while the row is over its own slot`()
    {
        (0..2).forEach { assertThat(reorderShift(index = it, from = 1, to = 1, extent = extent)).isZero() }
    }

    @Test
    fun `the slot the dropped row lands in is exactly the room the other rows made`()
    {
        // GIVEN rows of 100, 60 and 80 with a gap of 12, the first one dropped last
        val heights = listOf(100f, 60f, 80f)
        val gap = 12f

        // WHEN
        val oldTop = rowTops(heights, gap)[0]
        val newTop = rowTops(moveItem(heights, from = 0, to = 2), gap)[2]

        // THEN it moved down by the heights (and gaps) of the two rows it jumped over
        assertThat(newTop - oldTop).isCloseTo((60f + gap) + (80f + gap), Offset.offset(0.001f))
    }
}

class DragRangeTest
{
    private val heights = listOf(100f, 60f, 80f)

    @Test
    fun `the first row cannot go up and the last cannot go down`()
    {
        assertThat(dragRange(heights, 12f, from = 0).start).isCloseTo(0f, Offset.offset(0.001f))
        assertThat(dragRange(heights, 12f, from = 2).endInclusive).isCloseTo(0f, Offset.offset(0.001f))
    }

    @Test
    fun `a middle row can travel to either end of the list`()
    {
        // GIVEN tops 0, 112, 184 and a total height of 264
        val range = dragRange(heights, 12f, from = 1)

        // THEN up to the top edge, down until its bottom meets the list's bottom
        assertThat(range.start).isEqualTo(-112f)
        assertThat(range.endInclusive).isEqualTo(264f - 112f - 60f)
    }
}

class EdgeScrollSpeedTest
{
    // A visible area from 100 to 900, with a 100-wide border zone.
    private fun speed(pointerY: Float) = edgeScrollSpeed(pointerY, top = 100f, bottom = 900f, edge = 100f, maxSpeed = 20f)

    @Test
    fun `no scrolling while the finger is in the middle`()
    {
        assertThat(speed(500f)).isZero()
        assertThat(speed(250f)).isZero()
        assertThat(speed(750f)).isZero()
    }

    @Test
    fun `scrolls up near the top and down near the bottom`()
    {
        assertThat(speed(150f)).isLessThan(0f)
        assertThat(speed(850f)).isGreaterThan(0f)
    }

    @Test
    fun `goes faster the closer the finger is to the edge`()
    {
        assertThat(speed(110f)).isLessThan(speed(190f))
        assertThat(speed(890f)).isGreaterThan(speed(810f))
    }

    @Test
    fun `never exceeds the top speed, even beyond the visible area`()
    {
        assertThat(speed(100f)).isEqualTo(-20f)
        assertThat(speed(-500f)).isEqualTo(-20f)
        assertThat(speed(900f)).isEqualTo(20f)
        assertThat(speed(5_000f)).isEqualTo(20f)
    }
}
