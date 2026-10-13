package com.authvex.balaxysefactura.ui.screens.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.ClientRepository
import kotlinx.coroutines.launch

sealed interface ClientCreateUiState {
    object Idle : ClientCreateUiState
    object Saving : ClientCreateUiState
    data class Success(val docType: ClientDocumentType, val documentNumber: String) : ClientCreateUiState
    data class Error(val message: String) : ClientCreateUiState
}

class ClientCreateViewModel(
    private val clientRepository: ClientRepository
) : ViewModel() {

    var uiState: ClientCreateUiState by mutableStateOf(ClientCreateUiState.Idle)
        private set

    var docType by mutableStateOf(ClientDocumentType.RUC)
    var documentNumber by mutableStateOf("")
    var denominacion by mutableStateOf("")
    var direccion by mutableStateOf("")
    var ciudad by mutableStateOf("")
    var departamento by mutableStateOf("")
    var codigoPostal by mutableStateOf("")
    var email by mutableStateOf("")
    var telefono by mutableStateOf("")

    // DGI state
    var isQueryingDgi by mutableStateOf(false)
        private set
    var dgiMessage by mutableStateOf<String?>(null)
    var dgiInfo by mutableStateOf<DgiPersonaActEmpresarialResponseDto?>(null)

    fun onDocumentNumberChanged(input: String) {
        documentNumber = when (docType) {
            ClientDocumentType.RUC, ClientDocumentType.CI -> input.filter { it.isDigit() }
            else -> input
        }
    }

    fun onDocTypeChanged(type: ClientDocumentType) {
        docType = type
        documentNumber = when (type) {
            ClientDocumentType.RUC, ClientDocumentType.CI -> documentNumber.filter { it.isDigit() }
            else -> documentNumber
        }
    }

    fun isDgiQueryAvailable(): Boolean {
        val cleanRut = documentNumber.filter { it.isDigit() }
        return docType == ClientDocumentType.RUC && cleanRut.length == 12
    }

    fun queryDgi() {
        if (!isDgiQueryAvailable() || isQueryingDgi) return
        val cleanRut = documentNumber.filter { it.isDigit() }

        isQueryingDgi = true
        dgiMessage = null

        viewModelScope.launch {
            try {
                val result = clientRepository.getThirdPartyRegistralData(cleanRut)
                result.onSuccess { response ->
                    if (response.flag.equals("OK", ignoreCase = true)) {
                        dgiInfo = response
                        if (!response.denominacion.isNullOrBlank()) denominacion = response.denominacion
                        if (!response.direccionFiscal.isNullOrBlank()) {
                            direccion = response.direccionFiscal
                        } else if (response.domicilioFiscalPrincipal?.direccionFormateada?.isNotBlank() == true) {
                            direccion = response.domicilioFiscalPrincipal.direccionFormateada
                        }
                        if (!response.ciudad.isNullOrBlank()) {
                            ciudad = response.ciudad
                        } else if (!response.domicilioFiscalPrincipal?.ciudad.isNullOrBlank()) {
                            ciudad = response.domicilioFiscalPrincipal.ciudad!!
                        }
                        if (!response.departamento.isNullOrBlank()) {
                            departamento = response.departamento
                        } else if (!response.domicilioFiscalPrincipal?.departamento.isNullOrBlank()) {
                            departamento = response.domicilioFiscalPrincipal.departamento!!
                        }
                        if (!response.domicilioFiscalPrincipal?.codigoPostal.isNullOrBlank()) {
                            codigoPostal = response.domicilioFiscalPrincipal.codigoPostal!!
                        }
                        if (!response.email.isNullOrBlank()) email = response.email
                        if (!response.telefono.isNullOrBlank()) telefono = response.telefono

                        dgiMessage = "Datos autocompletados desde DGI"
                    } else {
                        dgiMessage = response.mensaje?.takeIf { it.isNotBlank() }
                            ?: "No se pudo consultar DGI. Puede completar los datos manualmente."
                    }
                }.onFailure {
                    dgiMessage = "No se pudo consultar DGI. Puede completar los datos manualmente."
                }
            } finally {
                isQueryingDgi = false
            }
        }
    }

    fun saveClient() {
        val cleanDoc = documentNumber.trim()
        val cleanDenom = denominacion.trim()

        if (cleanDoc.isBlank()) {
            uiState = ClientCreateUiState.Error("El número de documento es requerido")
            return
        }

        if (docType == ClientDocumentType.RUC && cleanDoc.length != 12) {
            uiState = ClientCreateUiState.Error("El RUC debe tener exactamente 12 dígitos")
            return
        }

        if (docType == ClientDocumentType.CI && cleanDoc.length != 8) {
            uiState = ClientCreateUiState.Error("La Cédula de Identidad debe tener exactamente 8 dígitos")
            return
        }

        if (cleanDenom.isBlank()) {
            uiState = ClientCreateUiState.Error("El nombre o razón social es requerido")
            return
        }

        if (cleanDenom.length > 150) {
            uiState = ClientCreateUiState.Error("El nombre no puede exceder los 150 caracteres")
            return
        }

        if (email.isNotBlank() && !email.contains("@")) {
            uiState = ClientCreateUiState.Error("El correo electrónico no es válido")
            return
        }

        if (telefono.isNotBlank() && telefono.filter { it.isDigit() }.length < 7) {
            uiState = ClientCreateUiState.Error("El teléfono debe contener al menos 7 dígitos")
            return
        }

        val esEmpresa = docType == ClientDocumentType.RUC

        val contacto = ContactoCreateDto(
            nombre = cleanDenom.take(100),
            direccion = direccion.trim().takeIf { it.isNotBlank() },
            ciudad = ciudad.trim().takeIf { it.isNotBlank() },
            estado = departamento.trim().takeIf { it.isNotBlank() },
            codigoPostal = codigoPostal.trim().takeIf { it.isNotBlank() },
            email = email.trim().takeIf { it.isNotBlank() },
            telefono = telefono.trim().takeIf { it.isNotBlank() }
        )

        val request = ClientCreateRequest(
            denominacion = cleanDenom,
            codigo = cleanDoc,
            tipoDocumentoIdentificacion = docType.apiValue,
            esEmpresa = esEmpresa,
            contacto = contacto
        )

        uiState = ClientCreateUiState.Saving

        viewModelScope.launch {
            val result = clientRepository.createClient(request)
            result.onSuccess {
                uiState = ClientCreateUiState.Success(docType, cleanDoc)
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = ClientCreateUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun resetState() {
        uiState = ClientCreateUiState.Idle
    }
}
