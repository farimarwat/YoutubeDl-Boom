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

With these improvements, **youtubedl-boom** is now more efficient, lightweight, and developer-friendly 🚀.  


## Installation

To use `youtubedl-boom` in your Android project, add the following dependency in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.farimarwat:youtubedl-boom:1.0.18")
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

#  📥 YoutubeDl Setup Guide  


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

  ### Version History
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
