package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CfeSearchRequest(
    @SerialName("offset")
    val offset: Int = 0,
    @SerialName("limit")
    val limit: Int = 20,
    @SerialName("query")
    val query: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("ordering")
    val ordering: String? = "Numero",
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("sortDirection")
    val sortDirection: String? = "desc"
)

@Serializable
data class PagedResponse<T>(
    val totalRecords: Int,
    val offset: Int,
    val limit: Int,
    val items: List<T>
)

@Serializable
data class CfeSearchResponse(
    val totalRecords: Int,
    val offset: Int,
    val limit: Int,
    val items: List<CfeSummaryDto>
)

@Serializable
data class CfeSummaryDto(
    val documentoId: Int,
    val serie: String?,
    val numero: Long?,
    val cfeCode: Int?,
    val receptor: String?,
    val fechaEmision: String?,
    val importeTotal: Double?,
    val monedaSimbolo: String?,
    val estadoCfe: Int?,
    val estadoReceptor: Int? = null
)

@Serializable
data class CfeDetailDto(
    val documentoId: Int = 0,
    val serie: String? = null,
    val numero: Long? = null,
    val cfeCode: Int? = null,
    val estadoCfe: Int? = null,
    val estadoReceptor: Int? = null,
    val receptor: String? = null,
    val fechaEmision: String? = null,
    val fechaConfirmacion: String? = null,
    val fechaEnvioUtc: String? = null,
    val fechaAceptadoUtc: String? = null,
    val importeTotal: Double? = null,
    val iva: Double? = null,
    val monedaCodigo: String? = null,
    val monedaSimbolo: String? = null,
    val ultimoError: String? = null
)
