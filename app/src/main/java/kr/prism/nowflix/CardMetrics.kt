package kr.prism.nowflix

/**
 * Card-width math for the home row. Kept in plain Float dp values (no Compose /
 * Android types) so it runs under a JVM unit test.
 */
object CardMetrics {
    /**
     * How many cards fill the row edge-to-edge before it starts scrolling.
     * This is a layout baseline, NOT the part count — with more parts than this
     * the extra cards keep the same width and scroll off-screen. Change this to
     * re-balance the row; the part count itself is always read from parts.json.
     */
    const val VISIBLE_COLUMNS = 6

    /**
     * Width of a single card so that [visibleColumns] of them exactly fill a row
     * of [rowWidthDp] (minus edge padding and inter-card gaps). With fewer parts
     * than [visibleColumns] the cards grow to fill the row; with more, width is
     * pinned to the [visibleColumns] fit and the surplus scrolls.
     */
    fun cardWidthDp(
        rowWidthDp: Float,
        itemCount: Int,
        sidePaddingDp: Float,
        gapDp: Float,
        visibleColumns: Int = VISIBLE_COLUMNS,
    ): Float {
        require(itemCount > 0) { "itemCount must be > 0" }
        require(rowWidthDp > 0f) { "rowWidthDp must be > 0" }
        val cols = minOf(itemCount, visibleColumns)
        val usable = rowWidthDp - sidePaddingDp * 2f - gapDp * (cols - 1)
        return usable / cols
    }
}
