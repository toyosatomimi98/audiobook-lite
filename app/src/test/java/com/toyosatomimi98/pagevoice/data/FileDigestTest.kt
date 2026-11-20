package com.toyosatomimi98.pagevoice.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class FileDigestTest {
    @Test
    fun `sha256 matches a standard vector`() {
        val file = Files.createTempFile("pagevoice-digest", ".txt").toFile()
        try {
            file.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                FileDigest.sha256(file),
            )
        } finally {
            file.delete()
        }
    }
}
