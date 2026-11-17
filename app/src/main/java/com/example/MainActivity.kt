package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppLanguage
import com.example.core.LocalAppStrings
import com.example.core.ThemeMode
import com.example.core.UserPreferences
import com.example.core.stringsFor
import com.example.ui.SongjogApp
import com.example.ui.theme.SongjogTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val preferences = remember { UserPreferences(applicationContext) }
            val currentLanguage by preferences.languageFlow.collectAsStateWithLifecycle(
                initialValue = AppLanguage.BENGALI
            )
            val currentThemeMode by preferences.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val windowSizeClass = calculateWindowSizeClass(this)

            SongjogTheme(themeMode = currentThemeMode) {
                CompositionLocalProvider(
                    LocalAppStrings provides stringsFor(currentLanguage)
                ) {
                    SongjogApp(
                        preferences = preferences,
                        currentLanguage = currentLanguage,
                        currentThemeMode = currentThemeMode,
                        widthSizeClass = windowSizeClass.widthSizeClass
                    )
                }
            }
        }
    }
}
