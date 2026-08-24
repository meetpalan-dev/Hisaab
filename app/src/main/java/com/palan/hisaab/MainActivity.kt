package com.palan.hisaab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.palan.hisaab.ui.nav.HisaabNavHost
import com.palan.hisaab.ui.theme.HisaabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as HisaabApplication).repository
        setContent {
            HisaabApp(repository)
        }
    }
}

@Composable
fun HisaabApp(repository: com.palan.hisaab.data.HisaabRepository) {
    HisaabTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            HisaabNavHost(repository = repository)
        }
    }
}
