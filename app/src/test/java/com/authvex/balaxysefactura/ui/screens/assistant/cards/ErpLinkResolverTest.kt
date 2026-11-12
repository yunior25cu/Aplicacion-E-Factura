package com.authvex.balaxysefactura.ui.screens.assistant.cards

import org.junit.Assert.*
import org.junit.Test

class ErpLinkResolverTest {

    private val webBaseUrl = "https://nice-ocean-0b5c5570f.5.azurestaticapps.net"

    @Test
    fun `resolve sale details path to native cfe detail route`() {
        val url = "https://nice-ocean-0b5c5570f.5.azurestaticapps.net/clients/details_sale/1043"

        val destination = ErpLinkResolver.resolve(url, webBaseUrl)

        assertTrue(destination is ErpLinkDestination.NativeCfeDetail)
        assertEquals(1043L, (destination as ErpLinkDestination.NativeCfeDetail).id)
    }

    @Test
    fun `resolve collection details path to native collection detail route`() {
        val url = "https://nice-ocean-0b5c5570f.5.azurestaticapps.net/clients/details_collection/7"

        val destination = ErpLinkResolver.resolve(url, webBaseUrl)

        assertTrue(destination is ErpLinkDestination.NativeCollectionDetail)
        assertEquals(7L, (destination as ErpLinkDestination.NativeCollectionDetail).id)
    }

    @Test
    fun `resolve other web path on matching host to external web destination`() {
        val url = "https://nice-ocean-0b5c5570f.5.azurestaticapps.net/reports/dashboard"

        val destination = ErpLinkResolver.resolve(url, webBaseUrl)

        assertTrue(destination is ErpLinkDestination.ExternalWeb)
        assertEquals(url, (destination as ErpLinkDestination.ExternalWeb).url)
    }

    @Test
    fun `resolve url with external host is ignored`() {
        val url = "https://malicious-external-site.com/clients/details_sale/1043"

        val destination = ErpLinkResolver.resolve(url, webBaseUrl)

        assertTrue(destination is ErpLinkDestination.Ignored)
    }

    @Test
    fun `resolve with empty WEB_BASE_URL is ignored`() {
        val url = "https://nice-ocean-0b5c5570f.5.azurestaticapps.net/clients/details_sale/1043"

        val destination = ErpLinkResolver.resolve(url, "")

        assertTrue(destination is ErpLinkDestination.Ignored)
    }
}
