package com.authvex.balaxysefactura.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiscalCompatibilityMatrixTest {

    // --- e-Ticket 101 Matrix Tests ---

    @Test
    fun `101 plus NIE is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.NIE, 101))
    }

    @Test
    fun `101 plus RUC is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.RUC, 101))
    }

    @Test
    fun `101 plus CI is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.CI, 101))
    }

    @Test
    fun `101 plus Otros is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.OTROS, 101))
    }

    @Test
    fun `101 plus Pasaporte is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.PASAPORTE, 101))
    }

    @Test
    fun `101 plus DNI is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.DNI, 101))
    }

    @Test
    fun `101 plus NIFE is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.NIFE, 101))
    }

    @Test
    fun `101 plus null document type is compatible`() {
        assertTrue(isClientCompatibleWithCfe(null, 101))
    }

    // --- e-Factura 111 Matrix Tests ---

    @Test
    fun `111 plus RUC is compatible`() {
        assertTrue(isClientCompatibleWithCfe(FiscalDocumentTypes.RUC, 111))
    }

    @Test
    fun `111 plus NIE is incompatible`() {
        assertFalse(isClientCompatibleWithCfe(FiscalDocumentTypes.NIE, 111))
    }

    @Test
    fun `111 plus CI is incompatible`() {
        assertFalse(isClientCompatibleWithCfe(FiscalDocumentTypes.CI, 111))
    }

    @Test
    fun `111 plus Otros is incompatible`() {
        assertFalse(isClientCompatibleWithCfe(FiscalDocumentTypes.OTROS, 111))
    }

    @Test
    fun `111 plus Pasaporte is incompatible`() {
        assertFalse(isClientCompatibleWithCfe(FiscalDocumentTypes.PASAPORTE, 111))
    }

    @Test
    fun `111 plus DNI is incompatible`() {
        assertFalse(isClientCompatibleWithCfe(FiscalDocumentTypes.DNI, 111))
    }

    @Test
    fun `111 plus NIFE is incompatible`() {
        assertFalse(isClientCompatibleWithCfe(FiscalDocumentTypes.NIFE, 111))
    }

    @Test
    fun `111 plus null document type is incompatible`() {
        assertFalse(isClientCompatibleWithCfe(null, 111))
    }
}
