package com.toyosatomimi98.pagevoice.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toyosatomimi98.pagevoice.data.BookFormat
import com.toyosatomimi98.pagevoice.data.LibraryBook
import com.toyosatomimi98.pagevoice.ui.theme.Ink
import com.toyosatomimi98.pagevoice.ui.theme.InkFaint
import com.toyosatomimi98.pagevoice.ui.theme.InkSoft
import com.toyosatomimi98.pagevoice.ui.theme.Navy
import com.toyosatomimi98.pagevoice.ui.theme.Ochre
import com.toyosatomimi98.pagevoice.ui.theme.Paper
import com.toyosatomimi98.pagevoice.ui.theme.PaperDeep
import com.toyosatomimi98.pagevoice.ui.theme.PaperLight
import com.toyosatomimi98.pagevoice.ui.theme.Plum
import com.toyosatomimi98.pagevoice.ui.theme.Sage
import com.toyosatomimi98.pagevoice.ui.theme.SageDeep
import com.toyosatomimi98.pagevoice.ui.theme.Terracotta
import com.toyosatomimi98.pagevoice.ui.theme.TerracottaSoft

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onImport: (Uri) -> Unit,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImport(uri)
    }
    val filteredBooks = remember(state.books, state.query) {
        val query = state.query.trim()
        if (query.isEmpty()) state.books else {
            state.books.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.author?.contains(query, ignoreCase = true) == true
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(PaperLight, Paper, PaperDeep.copy(alpha = 0.72f)),
                ),
            ),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                LibraryHeader(bookCount = state.books.size)
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SearchBox(
                    value = state.query,
                    onValueChange = onQueryChange,
                )
            }
            if (!state.isLoading && filteredBooks.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.query.isBlank()) "最近阅读" else "搜索结果",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${filteredBooks.size} 本",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkFaint,
                        )
                    }
                }
            }
            if (state.isLoading) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Terracotta,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else if (filteredBooks.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    EmptyLibrary(
                        isSearching = state.query.isNotBlank(),
                        isImporting = state.isImporting,
                        onImport = { importLauncher.launch(SUPPORTED_MIME_TYPES) },
                    )
                }
            } else {
                items(filteredBooks, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        onOpen = { onOpenBook(book.id) },
                        onDelete = { onDeleteBook(book.id) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.books.isNotEmpty() && !state.isLoading,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 22.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Button(
                onClick = { importLauncher.launch(SUPPORTED_MIME_TYPES) },
                enabled = !state.isImporting,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = PaperLight,
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                modifier = Modifier.height(54.dp),
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = PaperLight,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在解析…")
                } else {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导入一本书")
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 88.dp, start = 20.dp, end = 20.dp),
        )
    }
}

@Composable
private fun LibraryHeader(bookCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "页声",
                style = MaterialTheme.typography.displaySmall,
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "让文字留下声音，也让眼睛停在同一声调。",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
            )
            if (bookCount > 0) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = "书架上有 $bookCount 本书",
                    style = MaterialTheme.typography.labelMedium,
                    color = Terracotta,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        LibraryMark()
    }
}

@Composable
private fun LibraryMark() {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(PaperLight, TerracottaSoft),
                    start = Offset.Zero,
                    end = Offset(180f, 180f),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val pageColor = Ink
            val left = Size(size.width * 0.42f, size.height * 0.56f)
            drawRoundRect(
                color = pageColor,
                topLeft = Offset(size.width * 0.08f, size.height * 0.22f),
                size = left,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                color = pageColor,
                topLeft = Offset(size.width * 0.5f, size.height * 0.22f),
                size = left,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            )
            drawCircle(
                color = Terracotta,
                radius = size.minDimension * 0.095f,
                center = Offset(size.width * 0.5f, size.height * 0.49f),
            )
            drawArc(
                color = Terracotta,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(size.width * 0.61f, size.height * 0.35f),
                size = Size(size.width * 0.23f, size.height * 0.28f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        placeholder = {
            Text("搜索书名或作者", color = InkFaint)
        },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = InkSoft)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "清空搜索", tint = InkSoft)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = PaperLight.copy(alpha = 0.9f),
            unfocusedContainerColor = PaperLight.copy(alpha = 0.7f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Terracotta,
        ),
    )
}

@Composable
private fun BookCard(
    book: LibraryBook,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = book.progress.percent.coerceIn(0f, 1f),
        label = "book-progress",
    )
    val cover = coverColors(book.coverStyle)

    Column {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 22.dp, bottomEnd = 14.dp, bottomStart = 8.dp),
            color = Color.Transparent,
            shadowElevation = 7.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp),
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(
                        colors = cover,
                        start = Offset.Zero,
                        end = Offset(450f, 800f),
                    ),
                ),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.11f),
                        start = Offset(size.width * 0.18f, 0f),
                        end = Offset(size.width * 0.18f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    repeat(5) { index ->
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(size.width * 0.68f, size.height * (0.16f + index * 0.04f)),
                            end = Offset(size.width * 0.88f, size.height * (0.16f + index * 0.04f)),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        ),
                        radius = size.width * 0.64f,
                        center = Offset(size.width * 0.92f, size.height * 0.08f),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = if (book.format == BookFormat.PDF) "PDF" else "TEXT",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.88f),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.MoreHoriz,
                                    contentDescription = "更多",
                                    tint = Color.White.copy(alpha = 0.72f),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("从书架移除") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = book.title,
                        color = Color.White,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!book.author.isNullOrBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = book.author,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(CircleShape),
                        color = Color.White.copy(alpha = 0.9f),
                        trackColor = Color.White.copy(alpha = 0.18f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progressLabel(book),
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (book.progress.percent > 0f) {
                Icon(
                    Icons.Outlined.Headphones,
                    contentDescription = null,
                    tint = Terracotta,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("从书架移除？") },
            text = {
                Text("《${book.title}》的正文与阅读进度会从本机删除，无法在应用内恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("移除", color = Terracotta)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
            containerColor = PaperLight,
        )
    }
}

@Composable
private fun EmptyLibrary(
    isSearching: Boolean,
    isImporting: Boolean,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp, bottom = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(42.dp))
                .background(PaperDeep.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(92.dp).rotate(-4f)) {
                drawRoundRect(
                    color = PaperLight,
                    topLeft = Offset(size.width * 0.06f, size.height * 0.12f),
                    size = Size(size.width * 0.88f, size.height * 0.72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                )
                drawLine(
                    color = InkFaint.copy(alpha = 0.65f),
                    start = Offset(size.width * 0.2f, size.height * 0.38f),
                    end = Offset(size.width * 0.8f, size.height * 0.38f),
                    strokeWidth = 2.dp.toPx(),
                )
                drawLine(
                    color = InkFaint.copy(alpha = 0.4f),
                    start = Offset(size.width * 0.2f, size.height * 0.52f),
                    end = Offset(size.width * 0.66f, size.height * 0.52f),
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = Terracotta,
                    radius = 5.dp.toPx(),
                    center = Offset(size.width * 0.77f, size.height * 0.67f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (isSearching) "没有找到这本书" else "从一本书开始",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSearching) {
                "换一个书名或作者继续找。"
            } else {
                "导入 PDF、Markdown 或 TXT。书会留在本机，朗读也由系统完成。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        if (!isSearching) {
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onImport,
                enabled = !isImporting,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = PaperLight),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = PaperLight,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在解析…")
                } else {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件")
                }
            }
        }
    }
}

private fun coverColors(style: Int): List<Color> {
    return when (style % 6) {
        0 -> listOf(Navy, SageDeep)
        1 -> listOf(Terracotta, Plum)
        2 -> listOf(Sage, SageDeep)
        3 -> listOf(Ochre, Terracotta)
        4 -> listOf(Plum, Navy)
        else -> listOf(Color(0xFF687A73), Color(0xFF303F39))
    }
}

private fun progressLabel(book: LibraryBook): String {
    val percent = (book.progress.percent * 100).toInt()
    return when {
        book.progress.updatedAt == 0L -> "尚未开始"
        percent >= 99 -> "已读完"
        percent <= 0 -> "刚开始"
        else -> "读至 $percent%"
    }
}

private val SUPPORTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "text/markdown",
    "text/x-markdown",
    "text/plain",
    "application/octet-stream",
)
