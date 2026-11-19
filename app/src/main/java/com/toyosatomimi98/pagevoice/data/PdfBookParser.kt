package com.toyosatomimi98.pagevoice.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.max
import kotlin.math.min

class PdfBookParser(context: Context) {
    private val appContext = context.applicationContext

    fun parse(file: File, fallbackTitle: String): BookContent {
        PDFBoxResourceLoader.init(appContext)

        return PDDocument.load(file).use { document ->
            val pageList = document.pages
            if (pageList.count == 0) {
                throw IllegalArgumentException("PDF 中没有页面。")
            }

            var blankPages = 0
            val chapters = buildList {
                for (pageIndex in 0 until pageList.count) {
                    val page = pageList[pageIndex]
                    val stripper = PageTextStripper(page)
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    val extracted = runCatching { stripper.getText(document) }
                        .getOrNull()
                        .orEmpty()
                        .trim()
                    if (extracted.isBlank()) blankPages++

                    add(
                        BookChapter(
                            id = "pdf-page-$pageIndex",
                            title = pageTitle(extracted, pageIndex),
                            text = extracted,
                            sourcePageIndex = pageIndex,
                            textRegions = stripper.regions,
                        ),
                    )
                }
            }

            val outline = mutableListOf<TocEntry>()
            runCatching {
                collectOutline(
                    document,
                    document.documentCatalog.documentOutline?.firstChild,
                    depth = 0,
                    output = outline,
                )
            }
            if (outline.isEmpty()) {
                chapters.forEachIndexed { index, chapter ->
                    outline += TocEntry(
                        title = "第 ${index + 1} 页 · ${chapter.title}",
                        chapterIndex = index,
                        pageIndex = index,
                    )
                }
            }

            val info = document.documentInformation
            val infoTitle = info.title?.trim()?.takeIf { it.isNotEmpty() }
            val infoAuthor = info.author?.trim()?.takeIf { it.isNotEmpty() }
            val firstPageTitle = chapters.firstOrNull()?.text
                ?.lineSequence()
                ?.map { it.trim() }
                ?.firstOrNull { it.length in 2..80 }

            BookContent(
                detectedTitle = infoTitle ?: firstPageTitle ?: fallbackTitle,
                detectedAuthor = infoAuthor,
                chapters = chapters,
                toc = outline,
                isTextExtractionLimited = blankPages > pageList.count / 2,
            )
        }
    }

    private fun collectOutline(
        document: PDDocument,
        first: PDOutlineItem?,
        depth: Int,
        output: MutableList<TocEntry>,
    ) {
        var item = first
        while (item != null) {
            val title = item.title?.trim().orEmpty().ifBlank { "未命名目录" }
            val page = runCatching { item.findDestinationPage(document) }.getOrNull()
            val pageIndex = if (page == null) -1 else document.pages.indexOf(page)
            if (pageIndex >= 0) {
                output += TocEntry(
                    title = title,
                    chapterIndex = pageIndex,
                    pageIndex = pageIndex,
                    depth = depth,
                )
            }
            collectOutline(document, item.firstChild, depth + 1, output)
            item = item.nextSibling
        }
    }

    private fun pageTitle(extracted: String, pageIndex: Int): String {
        val firstLine = extracted.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(36)
        return firstLine ?: "第 ${pageIndex + 1} 页"
    }

    private class PageTextStripper(private val page: PDPage) : PDFTextStripper() {
        val regions = mutableListOf<TextRegion>()
        private val output = StringBuilder()

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            val visibleText = text.replace('\u00A0', ' ').trimEnd()
            if (visibleText.isBlank()) return

            val start = output.length
            output.append(visibleText).append('\n')
            if (textPositions.isEmpty()) return

            val box = page.cropBox
            val width = max(box.width, 1f)
            val height = max(box.height, 1f)
            val left = textPositions.minOf { it.xDirAdj }
            val right = textPositions.maxOf { it.xDirAdj + it.widthDirAdj }
            val top = textPositions.minOf { it.yDirAdj - it.heightDir }
            val bottom = textPositions.maxOf { it.yDirAdj }

            regions += TextRegion(
                startOffset = start,
                endOffset = start + visibleText.length,
                left = ((left - box.lowerLeftX) / width).coerceIn(0f, 1f),
                top = ((top - box.lowerLeftY) / height).coerceIn(0f, 1f),
                right = ((right - box.lowerLeftX) / width).coerceIn(0f, 1f),
                bottom = ((bottom - box.lowerLeftY) / height).coerceIn(0f, 1f),
            )
        }

        override fun getText(document: PDDocument): String {
            output.setLength(0)
            regions.clear()
            super.getText(document)
            return output.toString()
        }
    }
}
