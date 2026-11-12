package com.authvex.balaxysefactura.ui.screens.assistant

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
}
