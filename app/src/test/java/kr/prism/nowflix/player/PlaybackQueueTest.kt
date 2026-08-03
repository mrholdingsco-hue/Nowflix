package kr.prism.nowflix.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {

    @Test
    fun advancesToTheNextVideoInTheMiddleOfTheList() {
        assertEquals(1, PlaybackQueue.nextIndex(currentIndex = 0, size = 5))
        assertEquals(3, PlaybackQueue.nextIndex(currentIndex = 2, size = 5))
    }

    @Test
    fun lastVideoHasNoNextSoPlaybackReturnsToTheList() {
        assertNull(PlaybackQueue.nextIndex(currentIndex = 4, size = 5))
        assertTrue(PlaybackQueue.isLast(currentIndex = 4, size = 5))
    }

    @Test
    fun singleVideoPlaylistIsImmediatelyTheLastVideo() {
        assertNull(PlaybackQueue.nextIndex(currentIndex = 0, size = 1))
        assertTrue(PlaybackQueue.isLast(currentIndex = 0, size = 1))
    }

    @Test
    fun nonLastVideoIsNotFlaggedAsLast() {
        assertFalse(PlaybackQueue.isLast(currentIndex = 0, size = 3))
        assertFalse(PlaybackQueue.isLast(currentIndex = 1, size = 3))
    }

    @Test
    fun emptyOrOutOfRangeIndexHasNoNext() {
        assertNull(PlaybackQueue.nextIndex(currentIndex = 0, size = 0))
        assertNull(PlaybackQueue.nextIndex(currentIndex = 9, size = 5))
        assertNull(PlaybackQueue.nextIndex(currentIndex = 5, size = 5))
    }
}
