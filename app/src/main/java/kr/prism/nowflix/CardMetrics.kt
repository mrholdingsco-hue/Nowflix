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
     *
     * The cards are now vertical posters (see [POSTER_HEIGHT_RATIO]), so a full row
     * of them can be taller than the space the row is given. When [availableHeightDp]
     * is set, the width is additionally capped so that the whole card — poster plus the
     * part-name label reserved by [titleReserveDp] — fits the height. The smaller of the
     * two constraints wins, so six posters always fit on one screen without clipping.
     */
    fun cardWidthDp(
        rowWidthDp: Float,
        itemCount: Int,
        sidePaddingDp: Float,
        gapDp: Float,
        availableHeightDp: Float = Float.MAX_VALUE,
        titleReserveDp: Float = 0f,
        posterHeightRatio: Float = POSTER_HEIGHT_RATIO,
        visibleColumns: Int = VISIBLE_COLUMNS,
    ): Float {
        require(itemCount > 0) { "itemCount must be > 0" }
        require(rowWidthDp > 0f) { "rowWidthDp must be > 0" }
        val cols = minOf(itemCount, visibleColumns)
        val usable = rowWidthDp - sidePaddingDp * 2f - gapDp * (cols - 1)
        val byWidth = usable / cols
        if (availableHeightDp == Float.MAX_VALUE) return byWidth
        // card height = posterWidth * ratio + titleReserve  <=  availableHeight
        val byHeight = ((availableHeightDp - titleReserveDp).coerceAtLeast(0f)) / posterHeightRatio
        return minOf(byWidth, byHeight)
    }

    /** Poster artwork is a vertical 4:5 (width:height), i.e. height = 1.25 × width. */
    const val POSTER_HEIGHT_RATIO = 5f / 4f
}
