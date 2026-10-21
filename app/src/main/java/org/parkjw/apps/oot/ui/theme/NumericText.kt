package org.parkjw.apps.oot.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

private val NUMERIC_CHUNK = Regex("""[\d.,%/×:$\- ]+""")

fun numericRuns(text: String): List<IntRange> =
    NUMERIC_CHUNK.findAll(text)
        .mapNotNull { match ->
            if (!match.value.any { it.isDigit() }) return@mapNotNull null
            var start = match.range.first
            var end = match.range.last
            while (start <= end && text[start] == ' ') start++
            while (end >= start && text[end] == ' ') end--
            if (start > end) null else start..end
        }
        .toList()

fun withNumericSpans(text: String, numericStyle: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(text)
        for (range in numericRuns(text)) {
            addStyle(numericStyle, range.first, range.last + 1)
        }
    }
