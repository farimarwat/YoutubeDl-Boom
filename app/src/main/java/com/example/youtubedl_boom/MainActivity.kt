package com.example.youtubedl_boom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.youtubedl_boom.ui.theme.YoutubeDlBoomTheme
import com.farimarwat.helper.YoutubeDl
import com.farimarwat.helper.mapper.VideoInfo

import com.farimarwat.library.YoutubeDLResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())

       YoutubeDl.init(
            appContext = this,
            withFfmpeg = true,
            withAria2c = false,
            onSuccess = {
                Timber.i("Initialized: ${it}")
            },
            onError = {
                Timber.e(it)
            }
        )

        enableEdgeToEdge()
        setContent {
            var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
            var youtubeDLResponse: YoutubeDLResponse? = null
            var processId = ""
            val scope = rememberCoroutineScope()

            YoutubeDlBoomTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var url by remember { mutableStateOf("") }
                    var showScanProgress by remember { mutableStateOf(false) }
                    var downloadProgress by remember { mutableStateOf(0.0f) }
                    var downloadLine by remember { mutableStateOf("") }

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
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(12.dp)
                                    )
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
                                            com.farimarwat.helper.YoutubeDl.getInfo(
                                                url = url,
                                                onSuccess = {
                                                    showScanProgress = false
                                                    CoroutineScope(Dispatchers.Main).launch {
                                                       videoInfo= YoutubeDl.mapVideoInfo(it)
                                                    }

                                                },
                                                onError = { Timber.i(it) }
                                            )
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
                            Column {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (url.isNotEmpty()) {
                                                val request = YoutubeDl.createYoutubeDLRequest(url)

                                                //request.addOption("-f", "bv+ba")
                                                YoutubeDl.addOption(
                                                    request,
                                                    "-o",
                                                    StoragePermissionHelper.downloadDir.getAbsolutePath() + "/%(title)s.%(ext)s"
                                                )
                                                YoutubeDl.addOption(request,"--no-part")
                                                //request.addOption("--downloader", "ffmpeg")
                                                if (StoragePermissionHelper.checkAndRequestStoragePermission(
                                                        this@MainActivity
                                                    )
                                                ) {
                                                    YoutubeDl.download(
                                                        request = request,
                                                        pId = processId,
                                                        progressCallBack = { progress, eta, line ->
                                                            downloadProgress = progress
                                                            downloadLine = line
                                                            Timber.i("line: $line")
                                                        },
                                                        onStartProcess = { id ->
                                                            processId = id
                                                            Timber.i("ProcessId: ${id}")
                                                        },
                                                        onEndProcess = { response ->
                                                            Timber.i("YoutubeDlResponse: $response")
                                                        },
                                                        onError = { error ->
                                                            Timber.e("OnExecute: $error")
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_download_24),
                                        contentDescription = "Download"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Download",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        /*Timber.i("YoutubeDlResponse: ${youtubeDLResponse}")
                                        youtubeDl?.destroyProcessById(
                                            processId
                                        )*/
                                    }, shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                // Progress Bar
                                if (downloadProgress > 0) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Text showing Estimated Time Remaining (ETS)
                                    Text(
                                        text = "Estimated time: $downloadLine",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
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