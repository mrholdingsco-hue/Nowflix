package kr.prism.nowflix.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test

/** SHA-256 against published test vectors, so the PIN hashing matches the DB-stored hashes. */
class Sha256Test {

    @Test
    fun emptyStringVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(""),
        )
    }

    @Test
    fun abcVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc"),
        )
    }

    @Test
    fun isDeterministicAndLowercaseHex() {
        val h = Sha256.hex("739104")
        assertEquals(64, h.length)
        assertEquals(h, h.lowercase())
        assertEquals(h, Sha256.hex("739104"))
    }
}
