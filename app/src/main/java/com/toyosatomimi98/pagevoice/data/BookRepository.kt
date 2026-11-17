package com.toyosatomimi98.pagevoice.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class BookRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val rootDirectory = File(appContext.filesDir, "books")
    private val libraryFile = File(rootDirectory, "library.json")
    private val atomics = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _books = MutableStateFlow<List<LibraryBook>>(emptyList())
    val books: StateFlow<List<LibraryBook>> = _books.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        atomics.withLock {
            rootDirectory.mkdirs()
            val restored = runCatching {
                if (!libraryFile.exists()) emptyList() else {
                    json.decodeFromString(
                        ListSerializer(LibraryBook.serializer()),
                        libraryFile.readText(),
                    )
                }
            }.getOrElse { emptyList() }
            _books.value = restored.sortedByDescending { it.lastOpenedAt }
            _isLoaded.value = true
        }
    }

    suspend fun importBook(uri: Uri): ImportedBook = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = queryDisplayName(uri)
        val format = BookFormat.fromFileName(displayName)
            ?: throw IllegalArgumentException("只支持 PDF、Markdown（.md）和 TXT 文件。")
        val id = UUID.randomUUID().toString()
        val bookDirectory = directoryFor(id).apply { mkdirs() }
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "bin").lowercase()
        val localFileName = "source.$extension"
        val localFile = File(bookDirectory, localFileName)

        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选文件。" }
                localFile.outputStream().use { output -> input.copyTo(output) }
            }

            val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "未命名文档" }
            val parsedContent = when (format) {
                BookFormat.PDF -> PdfBookParser(appContext).parse(localFile, fallbackTitle)
                BookFormat.MARKDOWN, BookFormat.TEXT -> {
                    val parsed = MarkdownParser.parse(
                        source = MarkdownParser.decode(localFile.readBytes()),
                        fallbackTitle = fallbackTitle,
                    )
                    parsed.content
                }
            }
            writeContent(id, parsedContent)

            val now = System.currentTimeMillis()
            val book = LibraryBook(
                id = id,
                title = parsedContent.detectedTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                author = parsedContent.detectedAuthor,
                format = format,
                sourceFileName = displayName,
                localFileName = localFileName,
                importedAt = now,
                lastOpenedAt = now,
                coverStyle = (id.hashCode() and Int.MAX_VALUE) % 6,
            )
            atomics.withLock {
                val previousBooks = _books.value
                try {
                    _books.value = (listOf(book) + _books.value).distinctBy { it.id }
                    persistLibraryLocked()
                } catch (error: Exception) {
                    _books.value = previousBooks
                    throw error
                }
            }
            ImportedBook(book = book, content = parsedContent)
        } catch (error: Exception) {
            bookDirectory.deleteRecursively()
            throw error
        }
    }

    suspend fun loadContent(bookId: String): BookContent = withContext(Dispatchers.IO) {
        atomics.withLock {
            val file = contentFile(bookId)
            check(file.exists()) { "找不到书籍正文，请删除后重新导入。" }
            json.decodeFromString(BookContent.serializer(), file.readText())
        }
    }

    suspend fun findBook(bookId: String): LibraryBook? = withContext(Dispatchers.IO) {
        atomics.withLock { _books.value.firstOrNull { it.id == bookId } }
    }

    fun sourceFile(book: LibraryBook): File = File(directoryFor(book.id), book.localFileName)

    suspend fun touchBook(bookId: String) = withContext(Dispatchers.IO) {
        atomics.withLock {
            val now = System.currentTimeMillis()
            _books.value = _books.value
                .map { if (it.id == bookId) it.copy(lastOpenedAt = now) else it }
                .sortedByDescending { it.lastOpenedAt }
            persistLibraryLocked()
        }
    }

    suspend fun persistProgress(bookId: String, progress: ReadingProgress) = withContext(Dispatchers.IO) {
        atomics.withLock {
            _books.value = _books.value.map {
                if (it.id == bookId) {
                    it.copy(progress = progress, lastOpenedAt = progress.updatedAt)
                } else {
                    it
                }
            }.sortedByDescending { it.lastOpenedAt }
            persistLibraryLocked()
        }
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        atomics.withLock {
            val target = directoryFor(bookId)
            val canonicalRoot = rootDirectory.canonicalFile
            val canonicalTarget = target.canonicalFile
            check(canonicalTarget.parentFile == canonicalRoot) { "拒绝删除书籍目录之外的路径。" }
            _books.value = _books.value.filterNot { it.id == bookId }
            persistLibraryLocked()
            canonicalTarget.deleteRecursively()
        }
    }

    private fun persistLibraryLocked() {
        val atomicFile = AtomicFile(libraryFile)
        val stream = atomicFile.startWrite()
        try {
            stream.write(
                json.encodeToString(
                    ListSerializer(LibraryBook.serializer()),
                    _books.value,
                ).toByteArray(Charsets.UTF_8),
            )
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun writeContent(bookId: String, content: BookContent) {
        val file = contentFile(bookId)
        file.writeText(json.encodeToString(BookContent.serializer(), content))
    }

    private fun contentFile(bookId: String): File =
        File(directoryFor(bookId), "content.json")

    private fun directoryFor(bookId: String): File =
        File(rootDirectory, bookId)

    private fun queryDisplayName(uri: Uri): String {
        val resolver = appContext.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) {
                    cursor.getString(column)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "未命名文档"
    }
}
