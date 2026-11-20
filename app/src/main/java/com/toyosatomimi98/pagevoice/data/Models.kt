package com.toyosatomimi98.pagevoice.data

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat {
    PDF,
    MARKDOWN,
    TEXT,
    ;

    companion object {
        fun fromFileName(fileName: String): BookFormat? = when {
            fileName.endsWith(".pdf", ignoreCase = true) -> PDF
            fileName.endsWith(".md", ignoreCase = true) ||
                fileName.endsWith(".markdown", ignoreCase = true) -> MARKDOWN
            fileName.endsWith(".txt", ignoreCase = true) -> TEXT
            else -> null
        }
    }
}

@Serializable
data class ReadingProgress(
    val chapterIndex: Int = 0,
    val charOffset: Int = 0,
    val pageIndex: Int = 0,
    val percent: Float = 0f,
    val updatedAt: Long = 0L,
)

@Serializable
data class LibraryBook(
    val id: String,
    val title: String,
    val author: String? = null,
    val format: BookFormat,
    val sourceFileName: String,
    val localFileName: String,
    val sourceFingerprint: String? = null,
    val importedAt: Long,
    val lastOpenedAt: Long,
    val coverStyle: Int,
    val progress: ReadingProgress = ReadingProgress(),
)

@Serializable
data class TocEntry(
    val title: String,
    val chapterIndex: Int,
    val pageIndex: Int = 0,
    val depth: Int = 0,
)

@Serializable
data class TextRegion(
    val startOffset: Int,
    val endOffset: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class BookChapter(
    val id: String,
    val title: String,
    val text: String,
    val sourcePageIndex: Int? = null,
    val textRegions: List<TextRegion> = emptyList(),
)

@Serializable
data class BookContent(
    val detectedTitle: String? = null,
    val detectedAuthor: String? = null,
    val chapters: List<BookChapter>,
    val toc: List<TocEntry>,
    val isTextExtractionLimited: Boolean = false,
) {
    val totalCharacters: Int
        get() = chapters.sumOf { it.text.length.coerceAtLeast(1) }

    fun chapterPercent(chapterIndex: Int): Float {
        if (chapters.isEmpty()) return 0f
        if (chapters.size == 1) return 0f
        return chapterIndex.toFloat() / chapters.lastIndex.toFloat()
    }
}

data class SentenceSpan(
    val start: Int,
    val end: Int,
    val text: String,
)

data class ImportedBook(
    val book: LibraryBook,
    val content: BookContent,
)
