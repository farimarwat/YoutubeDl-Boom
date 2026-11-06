package com.example.youtubedl_boom

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.youtubedl_boom.ui.theme.YoutubeDlBoomTheme
import com.farimarwat.commons.VideoInfo
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        enableEdgeToEdge()
        setContent {
            YoutubeDlBoomTheme {
                SampleScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Asks only on Android 9 and older (Storage.permission is null on 10+).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.download()
        else Toast.makeText(context, "Storage permission is needed to save into Downloads", Toast.LENGTH_LONG).show()
    }
    val startDownload = {
        val permission = Storage.permission
        if (permission == null || Storage.hasPermission(context)) vm.download()
        else permissionLauncher.launch(permission)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("youtubedl-boom sample") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EngineCard(state.engine)
            UrlField(
                url = state.url,
                enabled = state.engine is Engine.Ready && !state.scanning,
                onUrlChange = vm::setUrl,
                onScan = vm::scan
            )
            if (state.scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading video info…")
                }
            }
            state.scanError?.let { ErrorText(it) }
            state.info?.let { InfoCard(it) }

            val busy = state.download is DownloadState.Running || state.download is DownloadState.Saving
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Mode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { vm.setMode(mode) },
                        enabled = !busy,
                        shape = SegmentedButtonDefaults.itemShape(index, Mode.entries.size)
                    ) { Text(if (mode == Mode.VIDEO) "Video (best)" else "Audio (mp3)") }
                }
            }
            Button(
                onClick = startDownload,
                enabled = state.engine is Engine.Ready && state.url.isNotEmpty() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(painterResource(R.drawable.baseline_download_24), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download", style = MaterialTheme.typography.titleMedium)
            }
            DownloadCard(state.download, onCancel = vm::cancel, onDismiss = vm::dismissDownload)
        }
    }
}

@Composable
private fun EngineCard(engine: Engine) {
    val flavor = if (BuildConfig.LITE) "youtubedl-boom-lite" else "youtubedl-boom (full)"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (engine) {
                    Engine.Preparing -> {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Preparing engine (first start unpacks Python)…")
                    }
                    is Engine.Ready -> Text("Ready · ${engine.ytdlpVersion ?: "yt-dlp"}", style = MaterialTheme.typography.titleSmall)
                    is Engine.Failed -> Text("Engine failed: ${engine.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(flavor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UrlField(url: String, enabled: Boolean, onUrlChange: (String) -> Unit, onScan: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val scan = { focus.clearFocus(); onScan() }
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("Video URL") },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { scan() }),
        trailingIcon = {
            if (url.isNotEmpty()) {
                IconButton(onClick = { onUrlChange("") }, enabled = enabled) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { clipboard.getText()?.text?.let(onUrlChange) },
            enabled = enabled
        ) { Text("Paste") }
        FilledTonalButton(onClick = scan, enabled = enabled && url.isNotEmpty()) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Scan")
        }
    }
}

@Composable
private fun InfoCard(info: VideoInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            info.thumbnail?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    info.title ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val details = listOfNotNull(
                    info.uploader,
                    info.duration.takeIf { it > 0 }?.let(::formatDuration),
                    (info.fileSize.takeIf { it > 0 } ?: info.fileSizeApproximate.takeIf { it > 0 })?.let(::formatSize),
                    info.extractorKey
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(download: DownloadState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    if (download is DownloadState.Idle) return
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (download) {
                is DownloadState.Running -> {
                    val known = download.percent > 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (known) "Downloading ${"%.1f".format(download.percent)}%" else "Working…",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (download.etaSeconds > 0) {
                            Text("ETA ${formatDuration(download.etaSeconds.toInt())}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (known) {
                        LinearProgressIndicator(
                            progress = { (download.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        download.line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                }
                DownloadState.Saving -> {
                    Text("Saving to Downloads/${Storage.FOLDER}…", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is DownloadState.Done -> {
                    Text("Saved", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2E7D32))
                    Text("Downloads/${Storage.FOLDER}/${download.fileName}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                        download.uri?.let { uri ->
                            Button(onClick = { open(context, uri) }) { Text("Open") }
                        }
                    }
                }
                is DownloadState.Failed -> {
                    Text("Download failed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    ErrorText(download.message)
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
                DownloadState.Canceled -> {
                    Text("Download canceled", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
                DownloadState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.error
    )
}

private fun open(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, context.contentResolver.getType(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%d KB".format(bytes / 1024)
}
