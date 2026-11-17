package com.toyosatomimi98.pagevoice

import android.app.Application
import com.toyosatomimi98.pagevoice.data.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PageVoiceApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val bookRepository: BookRepository by lazy { BookRepository(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { bookRepository.initialize() }
    }
}
