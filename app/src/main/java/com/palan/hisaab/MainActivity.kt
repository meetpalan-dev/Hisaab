package com.palan.hisaab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.palan.hisaab.data.HisaabSettings
import com.palan.hisaab.ui.nav.HisaabNavHost
import com.palan.hisaab.ui.theme.HisaabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HisaabApplication
        setContent {
            HisaabApp(app)
        }
    }
}

@Composable
fun HisaabApp(app: HisaabApplication) {
    val settings by app.settingsRepository.settings.collectAsState(initial = HisaabSettings())
    HisaabTheme(
        useMaterialYou = settings.useMaterialYou,
        themeMode = settings.themeMode,
        accentColor = settings.accentColor
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HisaabNavHost(repository = app.repository, settingsRepository = app.settingsRepository)
        }
    }
}
