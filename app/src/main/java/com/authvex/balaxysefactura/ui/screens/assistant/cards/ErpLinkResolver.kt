package com.authvex.balaxysefactura.ui.screens.assistant.cards

import java.net.URI

sealed class ErpLinkDestination {
    data class NativeCfeDetail(val id: Long) : ErpLinkDestination()
    data class NativeCollectionDetail(val id: Long) : ErpLinkDestination()
    data class ExternalWeb(val url: String) : ErpLinkDestination()
    object Ignored : ErpLinkDestination()
}

object ErpLinkResolver {

    fun resolve(urlStr: String?, webBaseUrl: String): ErpLinkDestination {
        if (urlStr.isNullOrBlank() || webBaseUrl.isBlank()) return ErpLinkDestination.Ignored

        return try {
            val urlUri = URI(urlStr)
            val baseUri = URI(webBaseUrl)

            if (!urlUri.scheme.equals("https", ignoreCase = true)) {
                return ErpLinkDestination.Ignored
            }

            if (!urlUri.host.equals(baseUri.host, ignoreCase = true)) {
                return ErpLinkDestination.Ignored
            }

            val path = urlUri.path ?: ""
            val salesRegex = Regex("^/clients/details_sale/(\\d+)$")
            val collectionsRegex = Regex("^/clients/details_collection/(\\d+)$")

            val salesMatch = salesRegex.find(path)
            if (salesMatch != null) {
                val id = salesMatch.groupValues[1].toLong()
                return ErpLinkDestination.NativeCfeDetail(id)
            }

            val collectionsMatch = collectionsRegex.find(path)
            if (collectionsMatch != null) {
                val id = collectionsMatch.groupValues[1].toLong()
                return ErpLinkDestination.NativeCollectionDetail(id)
            }

            ErpLinkDestination.ExternalWeb(urlStr)
        } catch (_: Exception) {
            ErpLinkDestination.Ignored
        }
    }
}
