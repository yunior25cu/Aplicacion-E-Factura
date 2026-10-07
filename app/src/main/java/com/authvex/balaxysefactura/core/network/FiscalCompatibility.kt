package com.authvex.balaxysefactura.core.network

object FiscalDocumentTypes {
    const val NIE = 1
    const val RUC = 2
    const val CI = 3
    const val OTROS = 4
    const val PASAPORTE = 5
    const val DNI = 6
    const val NIFE = 7
}

/**
 * Valida preventivamente en el cliente Android la compatibilidad entre la identidad fiscal
 * del cliente (tipoDocumentoIdentificacion) y el tipo de CFE (101 e-Ticket, 111 e-Factura),
 * siguiendo las reglas del Backend (CfeEmissionRules.cs).
 *
 * NOTA DE AUTORIDAD FISCAL:
 * - Backend prod es la autoridad fiscal definitiva.
 * - e-Factura (111): Requiere estrictamente TipoDocumentoIdentificacion = RUC (2).
 * - e-Ticket (101): Acepta tipos DGI explícitos 1..7 (incluyendo RUC 2). La ausencia de
 *   identificación (null) se permite a nivel UX como consumidor final no identificado,
 *   quedando su validez normativa (umbral de importe/retenciones) sujeta a validación final Backend.
 */
fun isClientCompatibleWithCfe(tipoDocumentoIdentificacion: Int?, cfeCode: Int): Boolean {
    return when (cfeCode) {
        101 -> {
            // e-Ticket 101: Acepta tipos DGI 1..7 o ausencia de identificación (null)
            tipoDocumentoIdentificacion == null || tipoDocumentoIdentificacion in FiscalDocumentTypes.NIE..FiscalDocumentTypes.NIFE
        }
        111 -> {
            // e-Factura 111: Requiere exclusivamente RUC (2)
            tipoDocumentoIdentificacion == FiscalDocumentTypes.RUC
        }
        else -> true
    }
}
