package com.storetd.play

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.storetd.play.navigation.StoreTdPlayNavHost
import com.storetd.play.ui.theme.StoreTdPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StoreTdPlayTheme {
                StoreTdPlayNavHost()
            }
        }
    }
}
