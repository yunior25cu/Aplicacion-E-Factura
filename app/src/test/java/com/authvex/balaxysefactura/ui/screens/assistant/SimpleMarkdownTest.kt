package com.authvex.balaxysefactura.ui.screens.assistant

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class SimpleMarkdownTest {

    @Test
    fun `parseMarkdownBlocks discards table separator row and uses first row as header`() {
        val markdown = """
            | Cliente | Deuda Vencida |
            | :--- | ---: |
            | ABITAB S A | $ 184.300 |
            | BARRETO | $ 25.000 |
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Table)

        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf("Cliente", "Deuda Vencida"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("ABITAB S A", "$ 184.300"), table.rows[0])
        assertEquals(listOf("BARRETO", "$ 25.000"), table.rows[1])
    }

    @Test
    fun `isNumericCell detects numeric values accurately`() {
        assertTrue(isNumericCell("184.300"))
        assertTrue(isNumericCell("$ 184.300"))
        assertTrue(isNumericCell("-12.5%"))
        assertTrue(isNumericCell("10.000,50"))

        assertFalse(isNumericCell("ABITAB S A"))
        assertFalse(isNumericCell("31-60 días"))
        assertFalse(isNumericCell("   "))
    }

    @Test
    fun `parseMarkdownBlocks table followed by paragraph produces two distinct blocks`() {
        val markdown = """
            | Producto | Total |
            | --- | --- |
            | Mesa | 100 |

            Resumen del periodo.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.Table)
        assertTrue(blocks[1] is MdBlock.Paragraph)

        assertEquals("Resumen del periodo.", (blocks[1] as MdBlock.Paragraph).text)
    }

    @Test
    fun `lines with pipe inside code block are not parsed as table`() {
        val markdown = """
            ```
            | line inside code |
            | not a table |
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.CodeBlock)
        val codeBlock = blocks[0] as MdBlock.CodeBlock
        assertTrue(codeBlock.text.contains("| line inside code |"))
    }

    @Test
    fun `parseMarkdownBlocks parses horizontal dividers for hyphens, asterisks and underscores`() {
        val markdown = """
            ---
            ***
            ___
            ----
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        assertEquals(4, blocks.size)
        assertTrue(blocks.all { it is MdBlock.Divider })
    }

    @Test
    fun `parseMarkdownBlocks does not treat double dash or bullet item as divider`() {
        val markdown = """
            --
            - item
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertEquals("--", (blocks[0] as MdBlock.Paragraph).text)
        assertTrue(blocks[1] is MdBlock.BulletItem)
        assertEquals("item", (blocks[1] as MdBlock.BulletItem).text)
    }

    @Test
    fun `divider line inside code block is not parsed as divider`() {
        val markdown = """
            ```
            ---
            ***
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.CodeBlock)
        val codeBlock = blocks[0] as MdBlock.CodeBlock
        assertTrue(codeBlock.text.contains("---"))
        assertFalse(blocks.any { it is MdBlock.Divider })
    }

    @Test
    fun `calculateTableColumnWidths gives numeric column its exact measured width despite very long text column`() {
        val headerWidths = listOf(80.dp, 60.dp)
        val rowWidths = listOf(
            listOf(320.dp, 75.dp),
            listOf(280.dp, 88.dp)
        )
        val isNumericColumns = listOf(false, true)

        val result = calculateTableColumnWidths(
            headerWidths = headerWidths,
            rowWidths = rowWidths,
            isNumericColumns = isNumericColumns,
            availableWidth = 260.dp
        )

        assertFalse(result.needsHorizontalScroll)
        assertEquals(88.dp, result.columnWidths[1])
        assertEquals(172.dp, result.columnWidths[0])
    }

    @Test
    fun `calculateTableColumnWidths distributes remaining width among text columns respecting minimum and needed width`() {
        // Case A: Both text columns exceed equal share -> split remaining width equally (>= 96.dp)
        val resultSplit = calculateTableColumnWidths(
            headerWidths = listOf(70.dp, 70.dp, 50.dp),
            rowWidths = listOf(listOf(220.dp, 200.dp, 60.dp)),
            isNumericColumns = listOf(false, false, true),
            availableWidth = 300.dp
        )
        assertFalse(resultSplit.needsHorizontalScroll)
        assertEquals(120.dp, resultSplit.columnWidths[0])
        assertEquals(120.dp, resultSplit.columnWidths[1])
        assertEquals(60.dp, resultSplit.columnWidths[2])

        // Case B: One text column fits completely (100.dp < 120.dp share) -> uses only 100.dp, giving rest (140.dp) to the long text column
        val resultFit = calculateTableColumnWidths(
            headerWidths = listOf(70.dp, 70.dp, 50.dp),
            rowWidths = listOf(listOf(250.dp, 100.dp, 60.dp)),
            isNumericColumns = listOf(false, false, true),
            availableWidth = 300.dp
        )
        assertFalse(resultFit.needsHorizontalScroll)
        assertEquals(140.dp, resultFit.columnWidths[0])
        assertEquals(100.dp, resultFit.columnWidths[1])
        assertEquals(60.dp, resultFit.columnWidths[2])
    }

    @Test
    fun `calculateTableColumnWidths enables horizontal scroll only when numeric plus text minimums exceed available width`() {
        val headerWidths = listOf(80.dp, 80.dp, 70.dp)
        val rowWidths = listOf(listOf(220.dp, 200.dp, 80.dp))
        val isNumericColumns = listOf(false, false, true)
        // Minimum required = 96.dp + 96.dp + 80.dp = 272.dp

        val fitsResult = calculateTableColumnWidths(
            headerWidths = headerWidths,
            rowWidths = rowWidths,
            isNumericColumns = isNumericColumns,
            availableWidth = 280.dp
        )
        assertFalse(fitsResult.needsHorizontalScroll)
        assertEquals(100.dp, fitsResult.columnWidths[0])
        assertEquals(100.dp, fitsResult.columnWidths[1])
        assertEquals(80.dp, fitsResult.columnWidths[2])

        val scrollsResult = calculateTableColumnWidths(
            headerWidths = headerWidths,
            rowWidths = rowWidths,
            isNumericColumns = isNumericColumns,
            availableWidth = 250.dp
        )
        assertTrue(scrollsResult.needsHorizontalScroll)
        assertEquals(96.dp, scrollsResult.columnWidths[0])
        assertEquals(96.dp, scrollsResult.columnWidths[1])
        assertEquals(80.dp, scrollsResult.columnWidths[2])
    }

    @Test
    fun `calculateTableColumnWidths shares exact same column widths between header and rows`() {
        // Column 0 (text) has wider row (150.dp > header 90.dp)
        // Column 1 (numeric) has wider header (95.dp > rows 70.dp and 80.dp)
        val headerWidths = listOf(90.dp, 95.dp)
        val rowWidths = listOf(
            listOf(150.dp, 70.dp),
            listOf(120.dp, 80.dp)
        )
        val isNumericColumns = listOf(false, true)

        val result = calculateTableColumnWidths(
            headerWidths = headerWidths,
            rowWidths = rowWidths,
            isNumericColumns = isNumericColumns,
            availableWidth = 300.dp
        )

        assertFalse(result.needsHorizontalScroll)
        assertEquals(2, result.columnWidths.size)
        assertEquals(150.dp, result.columnWidths[0])
        assertEquals(95.dp, result.columnWidths[1])
    }

    @Test
    fun `parseInlineMarkdown parses links in paragraph and bullet item with LinkAnnotation Url and underline primary style`() {
        val markdown = """
            Podés revisar el detalle en [Ver cliente en Balaxys](https://dev.balaxys.com/clients/details_client/337).
            - Ítem con [Ver factura](http://dev.balaxys.com/clients/details_sale/10)
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertTrue(blocks[1] is MdBlock.BulletItem)

        val primaryColor = Color(0xFF0055AA)
        val paragraphAnnotated = parseInlineMarkdown((blocks[0] as MdBlock.Paragraph).text, linkColor = primaryColor)
        assertEquals("Podés revisar el detalle en Ver cliente en Balaxys.", paragraphAnnotated.text)

        val pLinks = paragraphAnnotated.getLinkAnnotations(0, paragraphAnnotated.length)
        assertEquals(1, pLinks.size)
        val pLink = pLinks[0].item as LinkAnnotation.Url
        assertEquals("https://dev.balaxys.com/clients/details_client/337", pLink.url)
        assertEquals("Ver cliente en Balaxys", paragraphAnnotated.text.substring(pLinks[0].start, pLinks[0].end))
        assertEquals(primaryColor, pLink.styles?.style?.color)
        assertEquals(TextDecoration.Underline, pLink.styles?.style?.textDecoration)

        val bulletAnnotated = parseInlineMarkdown((blocks[1] as MdBlock.BulletItem).text, linkColor = primaryColor)
        assertEquals("Ítem con Ver factura", bulletAnnotated.text)

        val bLinks = bulletAnnotated.getLinkAnnotations(0, bulletAnnotated.length)
        assertEquals(1, bLinks.size)
        val bLink = bLinks[0].item as LinkAnnotation.Url
        assertEquals("http://dev.balaxys.com/clients/details_sale/10", bLink.url)
        assertEquals("Ver factura", bulletAnnotated.text.substring(bLinks[0].start, bLinks[0].end))
    }

    @Test
    fun `parseInlineMarkdown renders non-http schemes as plain text without link`() {
        val jsAnnotated = parseInlineMarkdown("Hacé clic en [Ejecutar](javascript:alert(1)) ahora")
        assertEquals("Hacé clic en Ejecutar ahora", jsAnnotated.text)
        assertTrue(jsAnnotated.getLinkAnnotations(0, jsAnnotated.length).isEmpty())

        val mailtoAnnotated = parseInlineMarkdown("Escribí a [Soporte](mailto:soporte@balaxys.com)")
        assertEquals("Escribí a Soporte", mailtoAnnotated.text)
        assertTrue(mailtoAnnotated.getLinkAnnotations(0, mailtoAnnotated.length).isEmpty())
    }

    @Test
    fun `parseInlineMarkdown supports link with bold text around it`() {
        val annotated = parseInlineMarkdown(
            "**Cliente:** [Ver cliente en Balaxys](https://dev.balaxys.com/clients/details_client/337) (**activo**)"
        )
        assertEquals("Cliente: Ver cliente en Balaxys (activo)", annotated.text)

        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, links.size)
        val link = links[0].item as LinkAnnotation.Url
        assertEquals("https://dev.balaxys.com/clients/details_client/337", link.url)
        assertEquals("Ver cliente en Balaxys", annotated.text.substring(links[0].start, links[0].end))

        val boldRanges = annotated.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(2, boldRanges.size)
        assertEquals("Cliente:", annotated.text.substring(boldRanges[0].start, boldRanges[0].end))
        assertEquals("activo", annotated.text.substring(boldRanges[1].start, boldRanges[1].end))
    }

    @Test
    fun `parseMarkdownBlocks parses single-line and multi-line quotes without greater-than prefix`() {
        val singleQuoteMd = "> Nota sobre el saldo pendiente"
        val singleBlocks = parseMarkdownBlocks(singleQuoteMd)
        assertEquals(1, singleBlocks.size)
        assertTrue(singleBlocks[0] is MdBlock.Quote)
        assertEquals("Nota sobre el saldo pendiente", (singleBlocks[0] as MdBlock.Quote).text)

        val multiQuoteMd = """
            > Primera línea de la cita
            > Segunda línea de la cita
            Párrafo posterior.
        """.trimIndent()
        val multiBlocks = parseMarkdownBlocks(multiQuoteMd)
        assertEquals(2, multiBlocks.size)
        assertTrue(multiBlocks[0] is MdBlock.Quote)
        assertEquals(
            "Primera línea de la cita\nSegunda línea de la cita",
            (multiBlocks[0] as MdBlock.Quote).text
        )
        assertTrue(multiBlocks[1] is MdBlock.Paragraph)
        assertEquals("Párrafo posterior.", (multiBlocks[1] as MdBlock.Paragraph).text)
    }

    @Test
    fun `greater-than line inside code block is not parsed as Quote`() {
        val markdown = """
            ```
            > esto está en un bloque de código
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.CodeBlock)
        assertFalse(blocks.any { it is MdBlock.Quote })
        assertEquals("> esto está en un bloque de código", (blocks[0] as MdBlock.CodeBlock).text)
    }
}
