package com.authvex.balaxysefactura.ui.screens.assistant

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
}
