package com.toyosatomimi98.pagevoice.ui.reader

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toyosatomimi98.pagevoice.data.BookContent
import com.toyosatomimi98.pagevoice.data.BookFormat
import com.toyosatomimi98.pagevoice.data.TextRegion
import com.toyosatomimi98.pagevoice.data.TocEntry
import com.toyosatomimi98.pagevoice.data.ReadingPreferences
import com.toyosatomimi98.pagevoice.data.ReaderVoicePreset
import com.toyosatomimi98.pagevoice.ui.theme.Highlight
import com.toyosatomimi98.pagevoice.ui.theme.HighlightStrong
import com.toyosatomimi98.pagevoice.ui.theme.Ink
import com.toyosatomimi98.pagevoice.ui.theme.InkFaint
import com.toyosatomimi98.pagevoice.ui.theme.InkSoft
import com.toyosatomimi98.pagevoice.ui.theme.Paper
import com.toyosatomimi98.pagevoice.ui.theme.PaperDeep
import com.toyosatomimi98.pagevoice.ui.theme.PaperLight
import com.toyosatomimi98.pagevoice.ui.theme.SageDeep
import com.toyosatomimi98.pagevoice.ui.theme.Terracotta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(
    bookId: String,
    application: Context,
    onClose: () -> Unit,
) {
    val app = application as Application
    val readerViewModel: ReaderViewModel = viewModel(
        key = "reader-$bookId",
        factory = ReaderViewModel.factory(app, bookId),
    )
    val state by readerViewModel.uiState.collectAsStateWithLifecycle()

    ReaderScreen(
        state = state,
        onClose = onClose,
        onChapterSelected = readerViewModel::onChapterSelected,
        onPageVisible = readerViewModel::onPageVisible,
        onTogglePlayback = readerViewModel::togglePlayback,
        onPreviousSentence = readerViewModel::previousSentence,
        onNextSentence = readerViewModel::nextSentence,
        onStartAtOffset = readerViewModel::startPlaybackAt,
        onCycleSpeed = readerViewModel::cycleSpeed,
        onVoicePreset = readerViewModel::setVoicePreset,
        onExportAudio = readerViewModel::startAudioExport,
        onMessageShown = readerViewModel::clearMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onClose: () -> Unit,
    onChapterSelected: (Int, Int) -> Unit,
    onPageVisible: (Int, Int) -> Unit,
    onTogglePlayback: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onStartAtOffset: (Int) -> Unit,
    onCycleSpeed: () -> Unit,
    onVoicePreset: (ReaderVoicePreset) -> Unit,
    onExportAudio: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val repository = (app as com.toyosatomimi98.pagevoice.PageVoiceApplication).bookRepository
    val preferences = remember(context) { ReadingPreferences(context) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var tocVisible by rememberSaveable { mutableStateOf(false) }
    var jumpVisible by rememberSaveable { mutableStateOf(false) }
    var typeVisible by rememberSaveable { mutableStateOf(false) }
    var voiceVisible by rememberSaveable { mutableStateOf(false) }
    var fontScale by rememberSaveable { mutableFloatStateOf(preferences.fontScale) }
    var lineSpacing by rememberSaveable { mutableFloatStateOf(preferences.lineSpacing) }
    var navigationRequest by remember { mutableStateOf<ReaderLocation?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        onExportAudio()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    LaunchedEffect(fontScale, lineSpacing) {
        preferences.fontScale = fontScale
        preferences.lineSpacing = lineSpacing
    }

    fun navigate(chapterIndex: Int, charOffset: Int) {
        navigationRequest = ReaderLocation(
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            token = System.nanoTime(),
        )
        onChapterSelected(chapterIndex, charOffset)
    }

    fun beginAudioExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onExportAudio()
        }
    }

    if (state.isLoading) {
        ReaderLoading()
        return
    }
    val content = state.content
    val book = state.book
    if (content == null || book == null || state.fatalError != null) {
        ReaderFailure(
            message = state.fatalError ?: "这本书暂时无法打开。",
            onClose = onClose,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
    ) {
        if (book.format == BookFormat.PDF) {
            PdfBookPager(
                state = state,
                content = content,
                sourceFile = repository.sourceFile(book),
                navigationRequest = navigationRequest,
                onNavigationConsumed = { navigationRequest = null },
                onPageVisible = onPageVisible,
                onChromeToggle = { chromeVisible = !chromeVisible },
            )
        } else {
            TextBookPager(
                state = state,
                content = content,
                navigationRequest = navigationRequest,
                onNavigationConsumed = { navigationRequest = null },
                onPageVisible = onPageVisible,
                onStartAtOffset = onStartAtOffset,
                onChromeToggle = { chromeVisible = !chromeVisible },
                fontScale = fontScale,
                lineSpacing = lineSpacing,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = book.title,
                chapterTitle = content.chapters
                    .getOrNull(state.currentChapterIndex)
                    ?.title
                    .orEmpty(),
                onClose = onClose,
                onToc = { tocVisible = true },
                onJump = { jumpVisible = true },
                onType = { typeVisible = true },
                onVoice = { voiceVisible = true },
                voicePreset = state.voicePreset,
                onExport = { beginAudioExport() },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible || state.isPlaying,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderTransport(
                state = state,
                progress = state.let {
                    if (book.format == BookFormat.PDF) {
                        (it.currentChapterIndex + 1f) / content.chapters.size
                    } else {
                        textProgress(content, it.currentChapterIndex, it.charOffset)
                    }
                },
                onTogglePlayback = onTogglePlayback,
                onPrevious = onPreviousSentence,
                onNext = onNextSentence,
                onCycleSpeed = onCycleSpeed,
                onJump = { jumpVisible = true },
                onVoice = { voiceVisible = true },
            )
        }

        if (!chromeVisible && !state.isPlaying) {
            Surface(
                color = Ink,
                shape = CircleShape,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 18.dp, bottom = 18.dp),
            ) {
                IconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier.size(54.dp),
                ) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = "开始朗读",
                        tint = PaperLight,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, bottom = 102.dp),
        )
    }

    if (tocVisible) {
        TocSheet(
            content = content,
            currentChapter = state.currentChapterIndex,
            onDismiss = { tocVisible = false },
            onSelect = { entry ->
                tocVisible = false
                navigate(entry.chapterIndex, 0)
            },
        )
    }
    if (jumpVisible) {
        JumpSheet(
            progress = state.let {
                if (book.format == BookFormat.PDF) {
                    (it.currentChapterIndex + 1f) / content.chapters.size
                } else {
                    textProgress(content, it.currentChapterIndex, it.charOffset)
                }
            },
            chapterTitle = content.chapters.getOrNull(state.currentChapterIndex)?.title.orEmpty(),
            onDismiss = { jumpVisible = false },
            onJump = { fraction ->
                jumpVisible = false
                val target = jumpLocation(content, book.format, fraction)
                navigate(target.first, target.second)
            },
        )
    }
    if (typeVisible) {
        TypographySheet(
            fontScale = fontScale,
            lineSpacing = lineSpacing,
            onFontScaleChange = { fontScale = it },
            onLineSpacingChange = { lineSpacing = it },
            onDismiss = { typeVisible = false },
        )
    }
    if (voiceVisible) {
        VoiceSheet(
            currentPreset = state.voicePreset,
            onSelect = onVoicePreset,
            onDismiss = { voiceVisible = false },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapterTitle: String,
    onClose: () -> Unit,
    onToc: () -> Unit,
    onJump: () -> Unit,
    onType: () -> Unit,
    onVoice: () -> Unit,
    voicePreset: ReaderVoicePreset,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(PaperLight, PaperLight.copy(alpha = 0.92f), Color.Transparent),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回书架", tint = Ink)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToc) {
                Icon(Icons.Outlined.Bookmarks, contentDescription = "目录", tint = Ink)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多", tint = Ink)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("快速跳转") },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onJump()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("排版") },
                        leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onType()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("朗读声音 · ${voicePreset.label}") },
                        leadingIcon = { Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onVoice()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出整本音频") },
                        leadingIcon = { Icon(Icons.Outlined.Headphones, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTransport(
    state: ReaderUiState,
    progress: Float,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onJump: () -> Unit,
    onVoice: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Paper.copy(alpha = 0.94f), Paper),
                ),
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            if (state.isPlaying && state.activeSentence != null) {
                Surface(
                    color = PaperLight.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp),
                ) {
                    Text(
                        text = state.activeSentence.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                color = Ink,
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 10.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Headphones,
                            contentDescription = null,
                            tint = PaperLight.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onJump),
                            color = Terracotta,
                            trackColor = PaperLight.copy(alpha = 0.15f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${(progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = PaperLight.copy(alpha = 0.65f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            onClick = onVoice,
                            color = PaperLight.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = PaperLight.copy(alpha = 0.8f),
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = state.voicePreset.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PaperLight,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = onPrevious) {
                            Icon(
                                Icons.Outlined.SkipPrevious,
                                contentDescription = "上一句",
                                tint = PaperLight,
                            )
                        }
                        Surface(
                            color = Terracotta,
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp),
                        ) {
                            IconButton(onClick = onTogglePlayback) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    contentDescription = if (state.isPlaying) "暂停" else "朗读",
                                    tint = PaperLight,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        IconButton(onClick = onNext) {
                            Icon(
                                Icons.Outlined.SkipNext,
                                contentDescription = "下一句",
                                tint = PaperLight,
                            )
                        }
                        Surface(
                            onClick = onCycleSpeed,
                            color = PaperLight.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = "${formatSpeed(state.speed)}×",
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = PaperLight,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBookPager(
    state: ReaderUiState,
    content: BookContent,
    navigationRequest: ReaderLocation?,
    onNavigationConsumed: () -> Unit,
    onPageVisible: (Int, Int) -> Unit,
    onStartAtOffset: (Int) -> Unit,
    onChromeToggle: () -> Unit,
    fontScale: Float,
    lineSpacing: Float,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 12)
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Serif,
        fontSize = (18f * fontScale).sp,
        lineHeight = (18f * fontScale * lineSpacing).sp,
        letterSpacing = 0.25.sp,
        color = Ink,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 26.dp, end = 26.dp, top = 72.dp, bottom = 102.dp),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val bodyHeight = maxHeight - 82.dp
        val lineHeightPx = with(density) { bodyStyle.lineHeight.toPx() }
        val maxLines = floor(with(density) { bodyHeight.toPx() } / lineHeightPx)
            .toInt()
            .coerceAtLeast(2)
        val pages = remember(content, bodyStyle, widthPx, maxLines) {
            TextPaginator.paginate(
                content = content,
                measurer = measurer,
                style = bodyStyle,
                widthPx = widthPx,
                maxLines = maxLines,
            )
        }
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有可显示的正文", color = InkSoft)
            }
            return@BoxWithConstraints
        }

        val initialPage = remember(pages) {
            pageForOffset(
                pages = pages,
                chapterIndex = state.currentChapterIndex,
                charOffset = state.charOffset,
            )
        }
        val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }

        LaunchedEffect(pagerState.currentPage) {
            val page = pages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
            onPageVisible(page.chapterIndex, page.start)
        }
        LaunchedEffect(navigationRequest) {
            navigationRequest ?: return@LaunchedEffect
            val target = pageForOffset(
                pages,
                navigationRequest.chapterIndex,
                navigationRequest.charOffset,
            )
            pagerState.animateScrollToPage(target)
            onNavigationConsumed()
        }
        LaunchedEffect(state.activeSentence, state.activeWordRange, state.isPlaying) {
            if (!state.isPlaying) return@LaunchedEffect
            val sentence = state.activeSentence ?: return@LaunchedEffect
            val followOffset = state.activeWordRange?.first ?: sentence.start
            val target = pageForOffset(pages, state.currentChapterIndex, followOffset)
            if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
        }
        LaunchedEffect(pages.size) {
            if (pagerState.currentPage !in pages.indices) {
                pagerState.scrollToPage(pages.lastIndex)
            }
        }
        LaunchedEffect(bodyStyle) {
            val target = pageForOffset(
                pages = pages,
                chapterIndex = state.currentChapterIndex,
                charOffset = state.charOffset,
            )
            if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 20.dp,
            key = { index -> "${pages[index].chapterIndex}:${pages[index].start}" },
        ) { pageIndex ->
            val page = pages[pageIndex]
            TextPage(
                page = page,
                chapterText = content.chapters[page.chapterIndex].text,
                bodyStyle = bodyStyle,
                chapterCount = content.chapters.size,
                pageNumber = pageIndex + 1,
                pageCount = pages.size,
                activeSentence = state.activeSentence
                    ?.takeIf { state.currentChapterIndex == page.chapterIndex },
                activeWordRange = state.activeWordRange
                    ?.takeIf { state.currentChapterIndex == page.chapterIndex },
                onStartAtOffset = onStartAtOffset,
                onChromeToggle = onChromeToggle,
            )
        }
    }
}

@Composable
private fun TextPage(
    page: ReaderPageSlice,
    chapterText: String,
    bodyStyle: TextStyle,
    chapterCount: Int,
    pageNumber: Int,
    pageCount: Int,
    activeSentence: com.toyosatomimi98.pagevoice.data.SentenceSpan?,
    activeWordRange: IntRange?,
    onStartAtOffset: (Int) -> Unit,
    onChromeToggle: () -> Unit,
) {
    val pageText = chapterText.substring(page.start, page.end)
    var textLayout by remember(pageText) { mutableStateOf<TextLayoutResult?>(null) }
    val annotated = remember(pageText, page.start, activeSentence, activeWordRange) {
        buildAnnotatedString {
            append(pageText)
            val sentence = activeSentence
            if (sentence != null) {
                val start = (sentence.start - page.start).coerceIn(0, pageText.length)
                val end = (sentence.end - page.start).coerceIn(start, pageText.length)
                if (start < end) {
                    addStyle(
                        SpanStyle(background = Highlight.copy(alpha = 0.52f)),
                        start,
                        end,
                    )
                }
            }
            val word = activeWordRange
            if (word != null) {
                val start = (word.first - page.start).coerceIn(0, pageText.length)
                val end = (word.last + 1 - page.start).coerceIn(start, pageText.length)
                if (start < end) {
                    addStyle(
                        SpanStyle(
                            background = HighlightStrong.copy(alpha = 0.82f),
                            fontWeight = FontWeight.SemiBold,
                        ),
                        start,
                        end,
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onChromeToggle() })
            },
    ) {
        Text(
            text = page.chapterTitle,
            style = MaterialTheme.typography.labelMedium,
            color = InkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(15.dp))
        Text(
            text = annotated,
            style = bodyStyle,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(textLayout, page.start) {
                    detectTapGestures { position ->
                        val layout = textLayout ?: return@detectTapGestures
                        onStartAtOffset(page.start + layout.getOffsetForPosition(position))
                    }
                },
            onTextLayout = { textLayout = it },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 ${page.chapterIndex + 1} / $chapterCount 章",
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint.copy(alpha = 0.72f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$pageNumber · $pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun PdfBookPager(
    state: ReaderUiState,
    content: BookContent,
    sourceFile: File,
    navigationRequest: ReaderLocation?,
    onNavigationConsumed: () -> Unit,
    onPageVisible: (Int, Int) -> Unit,
    onChromeToggle: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentChapterIndex.coerceIn(content.chapters.indices),
    ) { content.chapters.size }

    LaunchedEffect(pagerState.currentPage) {
        onPageVisible(pagerState.currentPage, 0)
    }
    LaunchedEffect(navigationRequest) {
        navigationRequest ?: return@LaunchedEffect
        pagerState.animateScrollToPage(
            navigationRequest.chapterIndex.coerceIn(content.chapters.indices),
        )
        onNavigationConsumed()
    }
    LaunchedEffect(state.activeSentence) {
        if (state.activeSentence != null && pagerState.currentPage != state.currentChapterIndex) {
            pagerState.animateScrollToPage(state.currentChapterIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 68.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
        pageSpacing = 18.dp,
        key = { index -> "pdf-$index" },
    ) { pageIndex ->
        PdfReaderPage(
            file = sourceFile,
            pageIndex = pageIndex,
            pageCount = content.chapters.size,
            regions = content.chapters[pageIndex].textRegions,
            activeRange = if (state.currentChapterIndex == pageIndex) {
                state.activeWordRange ?: state.activeSentence?.let { it.start until it.end }
            } else {
                null
            },
            extractionLimited = content.isTextExtractionLimited,
            onChromeToggle = onChromeToggle,
        )
    }
}

@Composable
private fun PdfReaderPage(
    file: File,
    pageIndex: Int,
    pageCount: Int,
    regions: List<TextRegion>,
    activeRange: IntRange?,
    extractionLimited: Boolean,
    onChromeToggle: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onChromeToggle() } },
        contentAlignment = Alignment.Center,
    ) {
        val requestedWidth = with(density) { maxWidth.roundToPx() }.coerceAtMost(1600)
        val bitmap by produceState<Bitmap?>(
            initialValue = null,
            key1 = file.absolutePath,
            key2 = pageIndex,
            key3 = requestedWidth,
        ) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    PdfPageRenderer.render(file, pageIndex, requestedWidth)
                }.getOrNull()
            }
        }

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(3.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                val currentBitmap = bitmap
                if (currentBitmap == null) {
                    CircularProgressIndicator(
                        color = Terracotta,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(currentBitmap.width.toFloat() / currentBitmap.height),
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = "PDF 第 ${pageIndex + 1} 页",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (activeRange == null) return@Canvas
                            regions.forEach { region ->
                                val overlaps = region.startOffset < activeRange.last + 1 &&
                                    region.endOffset > activeRange.first
                                if (overlaps) {
                                    drawRoundRect(
                                        color = HighlightStrong.copy(alpha = 0.34f),
                                        topLeft = Offset(
                                            region.left * size.width,
                                            region.top * size.height,
                                        ),
                                        size = Size(
                                            (region.right - region.left) * size.width,
                                            (region.bottom - region.top) * size.height,
                                        ),
                                        cornerRadius = CornerRadius(3.dp.toPx()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (extractionLimited) "扫描版 PDF · 文字朗读可能不可用" else "PDF 原版页面",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (extractionLimited) Terracotta else InkFaint,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pageIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkFaint,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    content: BookContent,
    currentChapter: Int,
    onDismiss: () -> Unit,
    onSelect: (TocEntry) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            Text("目录", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "共 ${content.chapters.size} 节",
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
            ) {
                itemsIndexed(content.toc, key = { index, entry -> "$index-${entry.title}" }) { _, entry ->
                    val selected = entry.chapterIndex == currentChapter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(entry) }
                            .background(if (selected) PaperDeep else Color.Transparent)
                            .padding(
                                start = (14 + entry.depth * 14).dp,
                                end = 14.dp,
                                top = 13.dp,
                                bottom = 13.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) Ink else InkSoft,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Terracotta,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Divider(color = PaperDeep.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpSheet(
    progress: Float,
    chapterTitle: String,
    onDismiss: () -> Unit,
    onJump: (Float) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text("快速跳转", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "${(sliderValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.displaySmall,
                color = Terracotta,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Terracotta,
                    activeTrackColor = Terracotta,
                    inactiveTrackColor = PaperDeep,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("开头", style = MaterialTheme.typography.labelMedium, color = InkFaint)
                Spacer(Modifier.weight(1f))
                Text("结尾", style = MaterialTheme.typography.labelMedium, color = InkFaint)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onJump(sliderValue) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = PaperLight,
                ),
            ) {
                Text("跳到这里")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSheet(
    currentPreset: ReaderVoicePreset,
    onSelect: (ReaderVoicePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            Text("朗读声音", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "优先使用系统已安装的对应声库；若设备只提供一个声库，会自动用音高形成两种声线。切换后会从当前句继续。",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
            )
            Spacer(Modifier.height(18.dp))
            ReaderVoicePreset.entries.forEach { preset ->
                val selected = currentPreset == preset
                Surface(
                    onClick = { onSelect(preset) },
                    color = if (selected) PaperDeep else Paper,
                    shape = RoundedCornerShape(20.dp),
                    border = if (selected) {
                        androidx.compose.foundation.BorderStroke(1.dp, Terracotta.copy(alpha = 0.45f))
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        Terracotta.copy(alpha = 0.16f)
                                    } else {
                                        PaperDeep.copy(alpha = 0.75f)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (selected) Terracotta else InkSoft,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSoft,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "当前声音",
                                tint = Terracotta,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypographySheet(
    fontScale: Float,
    lineSpacing: Float,
    onFontScaleChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text("排版", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(22.dp))
            SettingLabel("字号", "${(fontScale * 100).roundToInt()}%")
            Slider(
                value = fontScale,
                onValueChange = onFontScaleChange,
                valueRange = 0.78f..1.55f,
                colors = SliderDefaults.colors(
                    thumbColor = Terracotta,
                    activeTrackColor = Terracotta,
                    inactiveTrackColor = PaperDeep,
                ),
            )
            Spacer(Modifier.height(10.dp))
            SettingLabel("行距", "${(lineSpacing * 100).roundToInt()}%")
            Slider(
                value = lineSpacing,
                onValueChange = onLineSpacingChange,
                valueRange = 1.45f..2.15f,
                colors = SliderDefaults.colors(
                    thumbColor = Terracotta,
                    activeTrackColor = Terracotta,
                    inactiveTrackColor = PaperDeep,
                ),
            )
            Surface(
                color = Paper,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(
                    text = "夜色落下来，书页仍然安静。",
                    modifier = Modifier.padding(18.dp),
                    fontFamily = FontFamily.Serif,
                    fontSize = (18 * fontScale).sp,
                    lineHeight = (18 * fontScale * lineSpacing).sp,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun SettingLabel(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = Terracotta)
    }
}

@Composable
private fun ReaderLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = Terracotta,
            strokeWidth = 2.dp,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun ReaderFailure(message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Bookmarks,
            contentDescription = null,
            tint = Terracotta,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text("没能打开这本书", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = PaperLight),
        ) {
            Text("返回书架")
        }
    }
}

private fun pageForOffset(
    pages: List<ReaderPageSlice>,
    chapterIndex: Int,
    charOffset: Int,
): Int {
    val exact = pages.indexOfFirst {
        it.chapterIndex == chapterIndex && charOffset < it.end.coerceAtLeast(it.start + 1)
    }
    return if (exact >= 0) exact else pages.indexOfFirst { it.chapterIndex >= chapterIndex }.coerceAtLeast(0)
}

private fun textProgress(content: BookContent, chapterIndex: Int, charOffset: Int): Float {
    val before = content.chapters.take(chapterIndex).sumOf { it.text.length.coerceAtLeast(1) }
    val length = content.chapters.getOrNull(chapterIndex)?.text?.length?.coerceAtLeast(1) ?: 1
    return ((before + charOffset.coerceAtMost(length)).toFloat() / content.totalCharacters)
        .coerceIn(0f, 1f)
}

private fun jumpLocation(
    content: BookContent,
    format: BookFormat,
    fraction: Float,
): Pair<Int, Int> {
    val safe = fraction.coerceIn(0f, 1f)
    if (format == BookFormat.PDF) {
        return (safe * content.chapters.lastIndex.coerceAtLeast(0)).roundToInt() to 0
    }
    val target = (safe * content.totalCharacters).toInt()
    var consumed = 0
    content.chapters.forEachIndexed { index, chapter ->
        val length = chapter.text.length.coerceAtLeast(1)
        if (target <= consumed + length || index == content.chapters.lastIndex) {
            return index to (target - consumed).coerceIn(0, chapter.text.length)
        }
        consumed += length
    }
    return 0 to 0
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0" else {
        ((speed * 100).roundToInt() / 100f).toString()
    }
}

private data class ReaderLocation(
    val chapterIndex: Int,
    val charOffset: Int,
    val token: Long,
)
