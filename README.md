### ⚠️ Some major changes have been made in version 1.0.19. Before updating, please review the usage guide again; otherwise, you may encounter import issues.

## 🎩 Acknowledgment & Credits  

### 😔 Sorry for Java and x86, x64

Hats off to **JunkFood** for his outstanding work on <a href='https://github.com/yausername/youtubedl-android'>**youtubedl-android**!</a> 🎉  
His contribution to the Android community has made video downloading easier, and we deeply appreciate his efforts.  

🔹 **Our library, YoutubeDL-Boom, is still built on his foundation.**  
🔹 If you find this project useful and want to support the original work, **please donate to him, not us.** 🙌  

👉 **Visit <a href='https://github.com/yausername'>JunkFood's</a> Repository** and show some love! ❤️  

## Why We Modified It (The Need for youtubedl-boom)  

While **youtubedl-android** is an excellent library (huge thanks to JunkFood for their work ❤️), we found some areas that could be improved to make it more efficient and lightweight. Here’s what led us to create **youtubedl-boom**:  


### 2️⃣ FFmpeg Process Issue (Major Problem 🚨)  
- **Issue:** If a download is started using the FFmpeg downloader and the process is canceled, FFmpeg **keeps running in the background** and continues downloading, causing unnecessary resource consumption.  
- **Solution:** We **properly kill the child process** of FFmpeg when a download is canceled, ensuring no unwanted downloads occur.  

### 3️⃣ Missing Callbacks for FFmpeg Downloads  
- **Issue:** When using FFmpeg for downloading, there was **no callback string/result** to track progress or completion.  
- **Solution:** We now **capture and return output from the FFmpeg process**, giving proper feedback on download status.  

### 4️⃣ Expensive Thread Usage  
- **Issue:** The previous implementation relied on **threads**, which are **resource-heavy** and can lead to performance issues.  
- **Solution:** We replaced **threads with coroutines**, making the code more efficient and responsive.  

### 5️⃣ Unnecessary Code Complexity  
- **Issue:** youtubedl-android used **multiple modules** for simple tasks, making it harder to manage.  
- **Solution:** We **simplified the structure** by consolidating it into **a single module with a single dependency**, making integration much easier.

### 5️⃣ Helper API (in case of Dynamic Feature Module)  
- **Issue:** Writing reflection methods can be tedious when distributing the module via DFM.  
- **Solution:** We provide a seamless API to communicate with `youtubedl-boom` effortlessly.



With these improvements, **youtubedl-boom** is now more efficient, lightweight, and developer-friendly 🚀.  


## Installation

To use `youtubedl-boom` in your Android project, add the following dependency in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.farimarwat:youtubedl-boom:1.0.18")
}
```

To use `helper API` (Optional - In case of DFM). Details are below in the #2 section
```kotlin
dependencies {
    implementation("io.github.farimarwat:youtubedl-boom-helper:1.1")
}
```

## Proguard Rules
```kotlin
-keep class com.farimarwat.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
```

## Never Forget
Include this in app's manifest
```kotlin
<application
 android:extractNativeLibs="true"
...
>
...
</application>
```

#  📥 #1 YoutubeDl Setup Guide without DFM

**Note: If you are packaging the `youtubedl-boom` with the app then there is no need to use `Helper API` but it will increase the apk size**

## 🛠 Step 1: Declare a Global Variable  
We create a **nullable** global variable to store the `YoutubeDL` instance.  
This will be initialized **after a successful YouTubeDL setup**.  

```kotlin
var youtubeDl: YoutubeDL? = null
```
- This is usually done in the **App class** or an appropriate singleton.  
- Initially `null`, it will be set when initialization succeeds.  



## 🚀 Step 2: Initialize YouTubeDL  
 

```kotlin
YoutubeDL.init(
            appContext = this,
            withFfmpeg = true, //Default is false
            withAria2c = false, //Default is false
            onSuccess = {
                youtubeDl = it
            },
            onError = {
                Timber.e(it)
            }
        )
```

### ‼️ I recommend enabling FFMPEG, as it is required for some videos that are split. It is essential for merging audio and video after a successful download.


### 🔹 Example: Retrieving Video Information  

```kotlin
youtubeDl?.getInfo(
    url = "https://www.youtube.com/watch?v=example",
    onSuccess = { videoInfo ->
        println("Title: ${videoInfo.title}")
        println("Duration: ${videoInfo.duration}")
    },
    onError = { error ->
        println("Error: ${error.message}")
    }
)
```

## 📌 Explanation:  
- **`url`** → The YouTube video link.  
- **`onSuccess`** → Callback function that receives `VideoInfo` if retrieval succeeds.  
  - Prints the **title** and **duration** of the video.  
- **`onError`** → Callback function that handles errors if fetching fails.  
  - Prints the error message.  

### ⚙️ How It Works Behind the Scenes:  
- The **video extraction process runs in the background (IO thread)** using **Coroutines**, ensuring the main UI thread remains free.  
- The function spawns a separate process to execute **yt-dlp** with the `--dump-json` option to fetch video details.  
- Standard output (`stdout`) is captured and parsed into a structured `VideoInfo` object.  
- The `onSuccess` or `onError` callback is triggered based on the result.  

This ensures smooth, non-blocking execution while retrieving video metadata.  

## ⬇️ Downloading a Video

The `download` function allows you to download videos using `yt-dlp`. It runs asynchronously in the background and provides callbacks to track progress, handle errors, and retrieve the final response.

### 💪 Usage:

```kotlin
val request = YoutubeDLRequest("https://www.youtube.com/watch?v=example")

// Specify output directory and filename format
request.addOption("-o", StoragePermissionHelper.downloadDir.getAbsolutePath() + "/%(title)s.%(ext)s")

// Use ffmpeg as the downloader (optional) mostly used for live streams
request.addOption("--downloader", "ffmpeg")

val job = download(
    request = request,
    pId = "custom-process-id", // Optional. If not provided, an ID is auto-generated.
    progressCallBack = { percentage, elapsedTime, outputLine ->
        println("Progress: $percentage% | Time: $elapsedTime ms | yt-dlp output: $outputLine")

        // The `outputLine` contains raw yt-dlp output. Parse it if needed.
    },
    onStartProcess = { processId ->
        println("Download started with Process ID: $processId")
    },
    onEndProcess = { response ->
        println("Download completed in ${response.elapsedTime} ms")
        println("Output: ${response.out}")
    },
    onError = { error ->
        println("Download failed: ${error.message}")
    }
)
```
### 🔭 Explanation:

- **`request`**: A `YoutubeDLRequest` object that specifies the video URL and additional download options.
  - The `-o` option sets the output file location and naming format.
  - The `--downloader ffmpeg` option ensures `ffmpeg` is used for handling video conversions.

- **`pId`** *(optional)*: A unique Process ID to track the download. If omitted, an ID is auto-generated and returned via `onStartProcess`.

- **`progressCallBack`**:
  - `percentage`: The download progress in percentage.
  - `elapsedTime`: The time elapsed since the download started (in milliseconds).
  - `outputLine`: The raw output string from `yt-dlp`, which can contain useful details such as speed, estimated time remaining, or other metadata.

- **`onStartProcess`**: Invoked when the download process starts, providing the assigned Process ID.

- **`onEndProcess`**: Called when the download completes, returning a `YoutubeDLResponse` that includes:
  - `response.elapsedTime`: The total time taken for the download.
  - `response.out`: The standard output generated by `yt-dlp`.

- **`onError`**: Triggered if an error occurs during the download, returning the error message.


### 📝  Additional Notes:
- The download process executes **asynchronously in the background** using Kotlin Coroutines (`Dispatchers.IO`).
- `yt-dlp` generates detailed logs in `outputLine`, which can be parsed for specific information.
- The function ensures proper error handling and prevents duplicate Process IDs.

#  📥 #2 YoutubeDl Setup Guide with DFM

Declare a global flag `isInitialized` (may be in app class) and set it after successful initialization.  

## Initialization  
```kotlin
YoutubeDl.init(
    appContext = this,
    withFfmpeg = true, // Default is false
    withAria2c = false, // Default is false
    onSuccess = {
        isInitialized = true
    },
    onError = {
        Timber.e(it)
    }
)
```
Use the parameter `withFfmpeg` only if you want to download files with `FFMPEG`, and the same applies to `Aria2c`.  
**Note:** This `YoutubeDl` object is from the `helper` package. Do not confuse it with `YoutubeDl` from the `library` package.


## Getting Video Info  
```kotlin
YoutubeDl.getInfo(
    url = url,
    onSuccess = {
        val videoInfo = YoutubeDl.mapVideoInfo(it)
    },
    onError = { Timber.i(it) }
)
```
- `url`: A string URL for supported downloads.  
- `onSuccess`: Returns a `VideoInfo` object from the library package. To access it, we first need to convert it using the `mapVideoInfo()` function.  

**Note:** Never forget to check `isInitialized` to avoid unexpected behavior.  


## Download a Video  

First, create a download request:  

```kotlin
val request = YoutubeDl.createYoutubeDLRequest(url)
```

Next, add options. There are two ways: using a `Key-Value` pair or only a `Value`. Both are illustrated below:  

```kotlin
YoutubeDl.addOption(
    request,
    "-o",
    StoragePermissionHelper.downloadDir.getAbsolutePath() + "/%(title)s.%(ext)s"
)
YoutubeDl.addOption(request, "--no-part")
```

**Note:** `request` is passed witch we create above ☝️ 

Finally, call the `download` function:  

```kotlin
YoutubeDl.download(
    request = request,
    progressCallBack = { progress, eta, line ->
        // Handle progress updates
    },
    onStartProcess = { id ->
        // Handle process start
    },
    onEndProcess = { response ->
        // Handle process completion
    },
    onError = { error ->
        // Handle errors
    }
)
```

### To destroy a process:
```kotlin
YoutubeDl.destroyProcessById(processId)
```

### Update  

To update, first, create the update channel:  

```kotlin
val updateChannel = YoutubeDl.getUpdateChannel(YoutubeDl.CHANNEL_MASTER)
```

Next, pass this channel to the update function:  

```kotlin
YoutubeDl.updateYoutubeDL(
    appContext = this,
    updateChannel = updateChannel,
    onSuccess = {
        val status = YoutubeDl.mapUpdateStatus(it)
    },
    onError = {
        Timber.i(it)
    }
)
```

`mapUpdateStatus` is used to convert the status from the library package.  

### Get version and versionName
```kotlin
val version = YoutubeDl.version(this)
val versionName = YoutubeDl.versionName(this)

```


  ### Version History
  **youtubedl-boom:1.0.19**
  - Moved YoutubeDlRequest, YoutubeDlOption, YoutubeDlResponse, VideoInfo, VideoFormat to commons library
  - The above changes made because these are common between youtubedl-helper and youtubedl-boom
  
  **1.0.18**
  1. Removed dynamic download for library files.
  2. Now packaged with the library
  3. Fixed issue <a href='https://github.com/farimarwat/YoutubeDl-Boom/issues/7'>#7</a>
- 1.0.17

Fix ffmpeg initialization on initial setup
  
- 1.0.16

  Fix InterruptedIOException crash
  
- 1.0.15

    Fix OutOfMemoryError
  
- 1.0.14:
  
  Foreground service support added to download files
  
- 1.0.6:
  
  Fixed: failed to initialize <a href='https://github.com/farimarwat/YoutubeDl-Boom/issues/4'>#4</a>

## About Me  

I am a **Senior Mobile Developer** specializing in **Android and iOS**, with **7+ years of experience** in Android development. Additionally, I have **2+ years of experience** as a **Full-Stack Web Developer**, working with **PHP, Django, and Golang**.  

📌 **LinkedIn:** [Farman Ullah Marwat](https://www.linkedin.com/in/farman-ullah-marwat-a02859196/)  
