package dev.nekuz.appwall

object Labels {
    const val MAX_LENGTH = 100

    private val INVISIBLE_FILLERS = setOf(0x034F, 0x115F, 0x1160, 0x17B4, 0x17B5, 0x2800, 0x3164, 0xFFA0)
    private val REMOVED_TYPES = setOf(
        Character.CONTROL,
        Character.FORMAT,
        Character.SURROGATE,
        Character.PRIVATE_USE,
        Character.UNASSIGNED,
        Character.LINE_SEPARATOR,
        Character.PARAGRAPH_SEPARATOR,
    ).map { it.toInt() }.toSet()

    fun clean(raw: CharSequence?, packageName: String): String {
        val cleaned = StringBuilder()
        var count = 0
        var index = 0
        val text = raw ?: ""
        while (index < text.length && count < MAX_LENGTH) {
            val codePoint = Character.codePointAt(text, index)
            index += Character.charCount(codePoint)
            if (Character.getType(codePoint) in REMOVED_TYPES || codePoint in INVISIBLE_FILLERS) continue
            if (count == 0 && isSpace(codePoint)) continue
            cleaned.appendCodePoint(codePoint)
            count++
        }
        return cleaned.toString().trim().ifEmpty { packageName }
    }

    private fun isSpace(codePoint: Int) = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
}
