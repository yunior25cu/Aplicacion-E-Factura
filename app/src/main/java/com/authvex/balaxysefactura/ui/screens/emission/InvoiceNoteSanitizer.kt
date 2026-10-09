package com.authvex.balaxysefactura.ui.screens.emission

object InvoiceNoteSanitizer {

    fun sanitizeInvoiceNote(input: String): String {
        if (input.isEmpty()) return ""

        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val codePoint = input.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            if (!isEmojiCodePoint(codePoint)) {
                sb.appendCodePoint(codePoint)
            }

            i += charCount
        }

        return sb.toString().take(200)
    }

    private fun isEmojiCodePoint(cp: Int): Boolean {
        return when (cp) {
            in 0x1F600..0x1F64F -> true // Emoticons
            in 0x1F300..0x1F5FF -> true // Misc Symbols & Pictographs
            in 0x1F680..0x1F6FF -> true // Transport & Map
            in 0x1F1E6..0x1F1FF -> true // Flags
            in 0x2600..0x26FF -> true   // Misc Symbols (❤️, ☀️, ☔)
            in 0x2700..0x27BF -> true   // Dingbats (✅, ❌, ✂️)
            in 0x1F900..0x1F9FF -> true // Supplemental Symbols & Pictographs (🚀, 🤖)
            in 0x1FA70..0x1FAFF -> true // Symbols & Pictographs Extended-A
            in 0xFE00..0xFE0F -> true   // Variation Selectors
            0x200D -> true              // Zero Width Joiner (ZWJ)
            in 0x200B..0x200D -> true
            in 0x2190..0x21FF -> true   // Arrows
            in 0x2300..0x23FF -> true   // Misc Technical
            in 0x2B00..0x2BFF -> true   // Misc Symbols & Arrows (⭐, ⭕)
            0x203C, 0x2049, 0x2122, 0x2139, 0x25AA, 0x25AB, 0x25B6, 0x25C0, 0x25FB, 0x25FE, 0x3030, 0x303D, 0x3297, 0x3299 -> true
            else -> false
        }
    }
}
