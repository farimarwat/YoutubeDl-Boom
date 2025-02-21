## 🎩 Acknowledgment & Credits  

Hats off to **JunkFood** for his outstanding work on <a href='https://github.com/yausername/youtubedl-android'>**youtubedl-android**!</a> 🎉  
His contribution to the Android community has made video downloading easier, and we deeply appreciate his efforts.  

🔹 **Our library, YoutubeDL-Boom, is still built on his foundation.**  
🔹 If you find this project useful and want to support the original work, **please donate to him, not us.** 🙌  

👉 **Visit <a href='https://github.com/junkfood](https://github.com/yausername'>JunkFood's</a> Repository** and show some love! ❤️  

## Why We Modified It (The Need for youtubedl-boom)  

While **youtubedl-android** is an excellent library (huge thanks to JunkFood for their work ❤️), we found some areas that could be improved to make it more efficient and lightweight. Here’s what led us to create **youtubedl-boom**:  

### 1️⃣ App Size Optimization  
- **Issue:** youtubedl-android packages all necessary files inside the APK, leading to a larger app size.  
- **Solution:** We now **download only the required files** based on the device’s architecture during first-time initialization.  

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
    implementation("io.github.farimarwat:youtubedl-boom:1.0.3")
}
```

#  📥 YoutubeDl Setup Guide  

## 🔹 Overview  
`YoutubeDlFileManager` handles the downloading of required dependencies like **YouTubeDL, FFmpeg, and Aria2c**. This ensures minimal **APK size** by fetching only necessary files during the first install.  

---

## 🛠 Step 1: Declare a Global Variable  
We create a **nullable** global variable to store the `YoutubeDL` instance.  
This will be initialized **after a successful YouTubeDL setup**.  

```kotlin
var youtubeDl: YoutubeDL? = null
```
- This is usually done in the **App class** or an appropriate singleton.  
- Initially `null`, it will be set when initialization succeeds.  

---

##  ⚡ Step 2: Create the Manager Instance  
Before initializing YouTubeDL, we need a **manager** to handle dependency downloads.  

```kotlin
val manager = YoutubeDlFileManager
    .Builder()
    .withFFMpeg()  // Optional: Needed for merging split video downloads
    .withAria2c()  // Optional: For faster downloads
    .build()
```

- `.withFFMpeg()`: **Required if downloads are split into chunks** (e.g., video & audio separate).  
- `.withAria2c()`: **Optional** but can improve download performance.  
- **Manager ensures necessary files are downloaded on first install.**  

---

## 🚀 Step 3: Initialize YouTubeDL  
Once the manager is set up, we initialize **YouTubeDL**.  

```kotlin
val job = YoutubeDL.getInstance().init(
    appContext = this,  // Application context
    fileManager = manager,           // Required to download missing dependencies
    onSuccess = {
        youtubeDl = it  // Set the global variable
    },
    onError = {
        Timber.e(it)  // Log any errors
    }
)
```

### ✅ Explanation:  
- `fileManager = manager`: **Downloads required files. Downloads are done only one time when app is first executed.**  
- `youtubeDl = it`: Stores the initialized **YouTubeDL instance** for future use.  
- If initialization fails, the error is logged with `Timber.e(it)`.  

---

## 🎯 Summary  
✔️ **Step 1:** Create a global variable for `YoutubeDL`.  
✔️ **Step 2:** Build a **YoutubeDlFileManager** (optional FFmpeg & Aria2c).  
✔️ **Step 3:** Initialize `YoutubeDL` and download required dependencies on first install.  


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

