#!/usr/bin/env bash
# Builds the "lite" ffmpeg for youtubedl-boom-lite: one executable per ABI (libffmpeg.so),
# ffmpeg's own libraries and lame/opus/vorbis/mbedtls linked in statically, only Android system
# libraries (libc, libm, libdl, libz) loaded at run time. No zip, nothing to unpack at init.
#
# Keeps what yt-dlp uses: every demuxer, muxer, parser, bitstream filter, decoder and protocol
# (stream copy, merge, remux, fixups, HLS/https downloads), audio encoders for -x
# (mp3, aac/m4a, opus, vorbis, flac, alac, wav), image encoders for thumbnails, subtitle
# encoders for --embed-subs. Drops the external video encoders (x264, x265, vpx, aom, SvtAv1,
# rav1e) and heavy extras; --recode-video still works through ffmpeg's built-in mpeg4 encoder.
#
# Usage: scripts/ffmpeg-lite/build.sh [arm64-v8a|armeabi-v7a ...]   (default: both)
# Needs: Android NDK (ANDROID_NDK_HOME or ~/Library/Android/sdk/ndk/<NDK_VERSION>), the SDK's
# cmake, make, pkg-config, curl.
set -euo pipefail

NDK_VERSION=29.0.13599879
API=24
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK:-$ROOT/build/ffmpeg-lite}"
OUT="$ROOT/library-lite/src/main/ffmpegLibs"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK="${ANDROID_NDK_HOME:-$SDK/ndk/$NDK_VERSION}"
CMAKE="$(ls -d "$SDK"/cmake/*/bin 2>/dev/null | sort -V | tail -1)/cmake"
case "$(uname -s)" in Darwin) HOST=darwin-x86_64 ;; *) HOST=linux-x86_64 ;; esac
TC="$NDK/toolchains/llvm/prebuilt/$HOST"
JOBS="$(sysctl -n hw.ncpu 2>/dev/null || nproc)"

# name|url|sha256
SOURCES=(
  "ffmpeg-7.1.3|https://ffmpeg.org/releases/ffmpeg-7.1.3.tar.xz|f0bf043299db9e3caacb435a712fc541fbb07df613c4b893e8b77e67baf3adbe"
  "lame-3.100|https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz|ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e"
  "opus-1.5.2|https://downloads.xiph.org/releases/opus/opus-1.5.2.tar.gz|65c1d2f78b9f2fb20082c38cbe47c951ad5839345876e46941612ee87f9a7ce1"
  "libogg-1.3.6|https://downloads.xiph.org/releases/ogg/libogg-1.3.6.tar.xz|5c8253428e181840cd20d41f3ca16557a9cc04bad4a3d04cce84808677fa1061"
  "libvorbis-1.3.7|https://downloads.xiph.org/releases/vorbis/libvorbis-1.3.7.tar.xz|b33cc4934322bcbf6efcbacf49e3ca01aadbea4114ec9589d1b1e9d20f72954b"
  "mbedtls-3.6.5|https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-3.6.5/mbedtls-3.6.5.tar.bz2|4a11f1777bb95bf4ad96721cac945a26e04bf19f57d905f241fe77ebeddf46d8"
)

ENCODERS=libmp3lame,aac,libopus,libvorbis,flac,alac,pcm_s16le,pcm_s24le,pcm_f32le,mjpeg,png,movtext,srt,subrip,ass,ssa,webvtt,mpeg4
# Decoders: every audio and subtitle one people meet on the web (for -x and --convert-subs), image
# ones for thumbnails, and the common video ones (only needed when re-encoding; stream copy
# never decodes). Globs are expanded by ffmpeg's configure.
DECODERS="aac*,ac3*,eac3*,alac,als,ape,dca,flac,mp1*,mp2*,mp3*,opus,vorbis,pcm*,adpcm*,wma*,truehd,mlp,tta,wavpack,amrnb,amrwb,cook,ra_*,atrac*,nellymoser,gsm*,qdm2,speex,sipr,mace*,dsd*,ilbc,g72*,aptx*,sbc,libopus,libvorbis"
DECODERS="$DECODERS,ass,ssa,srt,subrip,webvtt,movtext,text,ccaption,dvbsub,dvdsub,pgssub,microdvd,subviewer*,sami,realtext,stl,pjs,jacosub,mpl2,vplayer"
DECODERS="$DECODERS,mjpeg*,png,apng,webp,gif,bmp,tiff,jpeg2000"
DECODERS="$DECODERS,h264,hevc,vp8,vp9,mpeg4,msmpeg4*,h263*,flv,mpeg1video,mpeg2video,theora,vp6*,wmv*,vc1,rawvideo"
FILTERS=aresample,aformat,anull,null,format,scale,crop,pad,setpts,asetpts,trim,atrim,copy,acopy,transpose,hflip,vflip,volume,concat,fps,setsar

fetch() {
  mkdir -p "$WORK/src"
  for entry in "${SOURCES[@]}"; do
    IFS='|' read -r name url sha <<<"$entry"
    local file="$WORK/src/$(basename "$url")"
    if [ ! -f "$file" ]; then curl -fsSL --retry 3 -o "$file" "$url"; fi
    echo "$sha  $file" | shasum -a 256 -c - >/dev/null || { echo "checksum mismatch: $file"; exit 1; }
  done
}

unpack() { # name -> fresh source dir for this ABI
  local name="$1" dir="$BUILD/$1"
  rm -rf "$dir"; mkdir -p "$BUILD"
  tar -xf "$WORK/src/$(ls "$WORK/src" | grep "^$name\.tar")" -C "$BUILD"
  echo "$dir"
}

autotools() { # name, extra configure args...
  local dir; dir="$(unpack "$1")"; shift
  (cd "$dir" && ./configure --host="$TRIPLE" --prefix="$PREFIX" --enable-static --disable-shared \
      --with-pic "$@" >/dev/null && make -j"$JOBS" >/dev/null && make install >/dev/null)
}

build_abi() {
  local abi="$1"
  case "$abi" in
    arm64-v8a)   TRIPLE=aarch64-linux-android;   CLANG=aarch64-linux-android$API-clang;     FF_ARCH=aarch64; FF_CPU=armv8-a ;;
    armeabi-v7a) TRIPLE=arm-linux-androideabi;   CLANG=armv7a-linux-androideabi$API-clang;  FF_ARCH=arm;     FF_CPU=armv7-a ;;
    *) echo "unknown ABI $abi"; exit 1 ;;
  esac
  BUILD="$WORK/$abi"; PREFIX="$BUILD/prefix"
  rm -rf "$BUILD"; mkdir -p "$PREFIX"
  export CC="$TC/bin/$CLANG" CXX="$TC/bin/$CLANG++" AR="$TC/bin/llvm-ar" RANLIB="$TC/bin/llvm-ranlib" \
         NM="$TC/bin/llvm-nm" STRIP="$TC/bin/llvm-strip"
  export CFLAGS="-O2 -fPIC" LDFLAGS="-Wl,-z,max-page-size=16384"
  export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig" PKG_CONFIG_PATH=""

  echo "[$abi] lame";   autotools lame-3.100 --disable-frontend --disable-decoder --disable-gtktest
  echo "[$abi] opus";   autotools opus-1.5.2 --disable-doc --disable-extra-programs
  echo "[$abi] ogg";    autotools libogg-1.3.6
  echo "[$abi] vorbis"; autotools libvorbis-1.3.7 --with-ogg="$PREFIX" --disable-examples --disable-docs

  echo "[$abi] mbedtls"
  local mb; mb="$(unpack mbedtls-3.6.5)"
  "$CMAKE" -S "$mb" -B "$mb/out" -G Ninja -DCMAKE_MAKE_PROGRAM="$(dirname "$CMAKE")/ninja" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$API" -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
    -DENABLE_PROGRAMS=OFF -DENABLE_TESTING=OFF -DUSE_SHARED_MBEDTLS_LIBRARY=OFF >/dev/null
  "$CMAKE" --build "$mb/out" --target install -j "$JOBS" >/dev/null

  echo "[$abi] ffmpeg"
  local ff; ff="$(unpack ffmpeg-7.1.3)"
  (cd "$ff" && ./configure \
      --target-os=android --arch="$FF_ARCH" --cpu="$FF_CPU" --enable-cross-compile \
      --cc="$CC" --cxx="$CXX" --ar="$AR" --ranlib="$RANLIB" --nm="$NM" --strip="$STRIP" \
      --sysroot="$TC/sysroot" --pkg-config=pkg-config --pkg-config-flags=--static \
      --prefix="$PREFIX" --enable-static --disable-shared --enable-pic \
      --extra-cflags="-I$PREFIX/include -O2" --extra-ldflags="-L$PREFIX/lib $LDFLAGS" \
      --disable-autodetect --disable-doc --disable-debug --disable-ffplay --disable-ffprobe \
      --disable-avdevice --disable-indevs --disable-outdevs --disable-hwaccels --disable-vulkan \
      --enable-version3 --enable-zlib --enable-network \
      --enable-libmp3lame --enable-libopus --enable-libvorbis --enable-mbedtls \
      --disable-encoders --enable-encoder="$ENCODERS" \
      --disable-decoders --enable-decoder="$DECODERS" \
      --disable-filters --enable-filter="$FILTERS" >"$BUILD/ffmpeg-configure.log" \
    && make -j"$JOBS" ffmpeg >/dev/null)

  mkdir -p "$OUT/$abi"
  "$STRIP" -o "$OUT/$abi/libffmpeg.so" "$ff/ffmpeg"
  echo "[$abi] done: $(du -h "$OUT/$abi/libffmpeg.so" | cut -f1)"
  rm -rf "$BUILD" # the sources are kept in $WORK/src; the build tree is large
}

fetch
for abi in "${@:-arm64-v8a armeabi-v7a}"; do
  for a in $abi; do build_abi "$a"; done
done
