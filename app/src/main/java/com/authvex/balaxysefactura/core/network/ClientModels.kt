package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.Serializable

enum class ClientDocumentType(val apiValue: Int, val label: String) {
    NIE(1, "NIE"),
    RUC(2, "RUC"),
    CI(3, "Cédula de Identidad"),
    OTHER(4, "Otros"),
    PASSPORT(5, "Pasaporte"),
    DNI(6, "DNI"),
    NIFE(7, "NIFE");

    companion object {
        fun fromApiValue(value: Int?): ClientDocumentType {
            return entries.find { it.apiValue == value } ?: OTHER
        }
    }
}

@Serializable
data class ContactoCreateDto(
    val nombre: String? = null,
    val cargo: String? = null,
    val ciudad: String? = null,
    val estado: String? = null,
    val condado: String? = null,
    val idPais: Long? = null,
    val codigoPais: String? = null,
    val codigoPostal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val web: String? = null,
    val direccion: String? = null,
    val email: String? = null,
    val telefono: String? = null
)

@Serializable
data class ClientCreateRequest(
    val denominacion: String,
    val codigo: String,
    val tipoDocumentoIdentificacion: Int,
    val esEmpresa: Boolean,
    val contacto: ContactoCreateDto? = null,
    val foto: String? = null,
    val recargo: Double? = null,
    val descuento: Double? = null,
    val cuentasBancarias: List<String>? = null,
    val ibank: String? = null,
    val swift: String? = null,
    val idTipoEmpresa: Long? = null,
    val idTipoCuenta: Long? = null,
    val idMoneda: Long? = null,
    val idEmpleado: Long? = null,
    val idVencimiento: Long? = null,
    val idFormaPago: Long? = null,
    val idTipoContribuyente: Long? = null,
    val idImpuesto: Long? = null,
    val saldo: Double? = null,
    val idListaPrecioPredeterminada: Long? = null,
    val idListaPrecios: List<Long>? = null
)

@Serializable
data class DgiDomicilioDto(
    val ciudad: String? = null,
    val departamento: String? = null,
    val codigoPostal: String? = null,
    val direccionFormateada: String? = null,
    val calle: String? = null,
    val numero: String? = null,
    val puerta: String? = null
)

@Serializable
data class DgiGiroDto(
    val codigo: String? = null,
    val descripcion: String? = null
)

@Serializable
data class DgiPersonaActEmpresarialResponseDto(
    val flag: String = "",
    val rut: String? = null,
    val denominacion: String? = null,
    val nombreFantasia: String? = null,
    val tipoEntidad: Int? = null,
    val descripcionTipoEntidad: String? = null,
    val estadoActividad: String? = null,
    val fechaInicioActividad: String? = null,
    val direccionFiscal: String? = null,
    val ciudad: String? = null,
    val departamento: String? = null,
    val email: String? = null,
    val telefono: String? = null,
    val celular: String? = null,
    val domicilioFiscalPrincipal: DgiDomicilioDto? = null,
    val domicilios: List<DgiDomicilioDto> = emptyList(),
    val giros: List<DgiGiroDto> = emptyList(),
    val mensaje: String? = null,
    val errores: List<String> = emptyList()
)
