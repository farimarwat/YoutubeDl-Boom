package com.example.youtubedl_boom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.youtubedl_boom.ui.theme.YoutubeDlBoomTheme
import com.farimarwat.downloadmanager.YoutubeDlFileManager
import com.farimarwat.library.YoutubeDL
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())

        enableEdgeToEdge()
        setContent {
            var videoInfo by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                val manager = YoutubeDlFileManager
                    .Builder()
                    .build()
                val job = YoutubeDL.getInstance().init(
                    appContext = this@MainActivity,
                    fileManager = manager,
                    onSuccess = {
                        it.getInfo(
                            url = "https://vimeo.com/22439234",
                            onSuccess = {
                                videoInfo = it.toString()
                            },
                            onError = {
                                videoInfo = it.toString()
                            }
                        )

                    },
                    onError = {
                        Timber.i(it)
                    }
                )
            }

            YoutubeDlBoomTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = videoInfo,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    YoutubeDlBoomTheme {
        Greeting("Android")
    }
}