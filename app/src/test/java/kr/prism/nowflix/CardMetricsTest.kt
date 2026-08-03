package kr.prism.nowflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMetricsTest {

    private val rowWidth = 1280f
    private val side = 24f
    private val gap = 12f

    private fun width(count: Int) =
        CardMetrics.cardWidthDp(rowWidth, count, side, gap)

    /** Total width the row content occupies: N cards + (N-1) gaps + both side pads. */
    private fun contentWidth(count: Int, cardWidth: Float) =
        count * cardWidth + (count - 1) * gap + side * 2f

    @Test
    fun sixCardsFillRowExactly() {
        val w = width(6)
        assertEquals(rowWidth, contentWidth(6, w), 0.01f)
    }

    @Test
    fun nineCardsKeepSixColumnWidthAndOverflow() {
        val six = width(6)
        val nine = width(9)
        // Width is pinned to the 6-column fit; extra cards do not shrink it.
        assertEquals(six, nine, 0.01f)
        // 9 cards no longer fit -> content is wider than the row -> it scrolls.
        assertTrue(contentWidth(9, nine) > rowWidth)
    }

    @Test
    fun fewerThanBaselineCardsGrowToFill() {
        // 3 parts should still fill the row edge-to-edge (cols = min(3, 6) = 3).
        val w = width(3)
        assertEquals(rowWidth, contentWidth(3, w), 0.01f)
        assertTrue(w > width(6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroItemsRejected() {
        width(0)
    }

    @Test
    fun tallPostersAreCappedToFitAvailableHeight() {
        // A short row area forces the height cap to win over the by-columns width, so six
        // vertical posters (height = 1.25 × width) plus the label reserve still fit.
        val shortHeight = 260f
        val titleReserve = 48f
        val capped = CardMetrics.cardWidthDp(
            rowWidthDp = rowWidth,
            itemCount = 6,
            sidePaddingDp = side,
            gapDp = gap,
            availableHeightDp = shortHeight,
            titleReserveDp = titleReserve,
        )
        val expectedByHeight = (shortHeight - titleReserve) / CardMetrics.POSTER_HEIGHT_RATIO
        assertEquals(expectedByHeight, capped, 0.01f)
        // The whole card (poster + reserved label) fits the available height.
        assertTrue(capped * CardMetrics.POSTER_HEIGHT_RATIO + titleReserve <= shortHeight + 0.01f)
        // And the cap is what bound it — it is narrower than the by-columns width.
        assertTrue(capped < width(6))
    }

    @Test
    fun tallEnoughRowKeepsByColumnWidth() {
        // With ample height the by-columns width is unchanged (height cap does not bind).
        val roomy = CardMetrics.cardWidthDp(
            rowWidthDp = rowWidth,
            itemCount = 6,
            sidePaddingDp = side,
            gapDp = gap,
            availableHeightDp = 4000f,
            titleReserveDp = 48f,
        )
        assertEquals(width(6), roomy, 0.01f)
    }
}
