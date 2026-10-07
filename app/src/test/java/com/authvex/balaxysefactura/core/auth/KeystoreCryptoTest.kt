package com.authvex.balaxysefactura.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeystoreCryptoTest {

    @Test
    fun `decrypt legacy unencrypted JWT token returns string as is`() {
        val legacyJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val result = KeystoreCrypto.decrypt(legacyJwt)
        assertEquals(legacyJwt, result)
    }

    @Test
    fun `decrypt corrupted payload returns null safely without crash`() {
        val corruptedBase64 = "Y29ycnVwdGVkX2RhdGFfYmFzZTY0"
        val result = KeystoreCrypto.decrypt(corruptedBase64)
        assertNull(result)
    }

    @Test
    fun `encrypt and decrypt null or empty returns null`() {
        assertNull(KeystoreCrypto.encrypt(null))
        assertNull(KeystoreCrypto.encrypt(""))
        assertNull(KeystoreCrypto.decrypt(null))
        assertNull(KeystoreCrypto.decrypt(""))
    }
}
