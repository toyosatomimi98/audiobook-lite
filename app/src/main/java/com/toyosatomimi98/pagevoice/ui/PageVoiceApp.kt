package com.toyosatomimi98.pagevoice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toyosatomimi98.pagevoice.ui.library.LibraryScreen
import com.toyosatomimi98.pagevoice.ui.library.LibraryViewModel
import com.toyosatomimi98.pagevoice.ui.reader.ReaderRoute

@Composable
fun PageVoiceApp() {
    val context = LocalContext.current
    val application = context.applicationContext
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(context.applicationContext as android.app.Application),
    )
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    var openBookId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = openBookId != null) {
        openBookId = null
    }

    if (openBookId == null) {
        LibraryScreen(
            state = libraryState,
            onQueryChange = libraryViewModel::updateQuery,
            onImport = libraryViewModel::importBook,
            onOpenBook = { openBookId = it },
            onDeleteBook = libraryViewModel::deleteBook,
            onMessageShown = libraryViewModel::clearMessage,
        )
    } else {
        ReaderRoute(
            bookId = checkNotNull(openBookId),
            application = application,
            onClose = { openBookId = null },
        )
    }
}
