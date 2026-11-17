package com.toyosatomimi98.pagevoice.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.toyosatomimi98.pagevoice.PageVoiceApplication
import com.toyosatomimi98.pagevoice.data.LibraryBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val query: String = "",
    val message: String? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PageVoiceApplication).bookRepository
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.books.collect { books ->
                _uiState.value = _uiState.value.copy(
                    books = books,
                    isLoading = !repository.isLoaded.value,
                )
            }
        }
        viewModelScope.launch {
            repository.isLoaded.collect { loaded ->
                _uiState.value = _uiState.value.copy(isLoading = !loaded)
            }
        }
    }

    fun updateQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun importBook(uri: Uri) {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, message = null)
            runCatching { repository.importBook(uri) }
                .onSuccess { imported ->
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        message = "《${imported.book.title}》已加入书架。",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        message = error.message ?: "导入失败，请换一个文件重试。",
                    )
                }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteBook(bookId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(message = "已从书架移除。")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(message = error.message ?: "移除失败。")
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(application) as T
                }
            }
    }
}
