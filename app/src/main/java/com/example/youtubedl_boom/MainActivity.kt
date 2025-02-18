package com.example.youtubedl_boom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.youtubedl_boom.ui.theme.YoutubeDlBoomTheme
import com.farimarwat.downloadmanager.YoutubeDlFileManager
import com.farimarwat.library.VideoInfo
import com.farimarwat.library.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        var youtubeDl: YoutubeDL? = null
        enableEdgeToEdge()
        setContent {
            var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                val manager = YoutubeDlFileManager
                    .Builder()
                    .build()

                val job = YoutubeDL.getInstance().init(
                    appContext = this@MainActivity,
                    fileManager = manager,
                    onSuccess = {
                        youtubeDl = it
                    },
                    onError = {
                        Timber.e(it)
                    }
                )
            }

            YoutubeDlBoomTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var url by remember { mutableStateOf("") }
                    var showScanProgress by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = { Text("Enter URL") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                FilledTonalIconButton(
                                    onClick = {
                                        scope.launch {
                                            showScanProgress = true
                                            youtubeDl?.let {
                                                it.getInfo(
                                                    url = url,
                                                    onSuccess = {
                                                        videoInfo = it
                                                        showScanProgress = false
                                                    },
                                                    onError = { Timber.i(it) }
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Find"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            videoInfo?.thumbnail?.let { thumbnailUrl ->
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = videoInfo?.title ?: "No title",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Size: ${(videoInfo?.fileSize ?: 0) / (1024 * 1024)} MB",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        if (showScanProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

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