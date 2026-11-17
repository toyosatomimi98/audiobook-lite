package com.toyosatomimi98.pagevoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.toyosatomimi98.pagevoice.ui.PageVoiceApp
import com.toyosatomimi98.pagevoice.ui.theme.PageVoiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PageVoiceTheme {
                PageVoiceApp()
            }
        }
    }
}
