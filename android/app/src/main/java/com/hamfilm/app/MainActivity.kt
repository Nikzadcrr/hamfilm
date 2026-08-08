package com.hamfilm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hamfilm.app.ui.navigation.AppNavigation
import com.hamfilm.app.ui.theme.HamfilmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HamfilmTheme {
                AppNavigation()
            }
        }
    }
}
