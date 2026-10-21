#!/usr/bin/env bash
set -euo pipefail
export CMAKE_BUILD_PARALLEL_LEVEL=2
cd "$(dirname "$0")/.."
if git -C vendor/FreeRDP apply --check "$PWD/scripts/patches/freerdp.patch" 2>/dev/null; then
    git -C vendor/FreeRDP apply "$PWD/scripts/patches/freerdp.patch"
else
    git -C vendor/FreeRDP apply --reverse --check "$PWD/scripts/patches/freerdp.patch"
fi
ndk="${ANDROID_NDK_HOME:-$HOME/android-sdk/android-ndk-r29}"
[[ -f "$ndk/build/cmake/android.toolchain.cmake" ]] || { echo 'Install Android NDK r29 or set ANDROID_NDK_HOME.' >&2; exit 1; }
# Google's Linux NDK ships x86 compilers. Use the ARM host compiler with the same Android sysroot/runtime.
if [[ $(uname -m) == aarch64 ]]; then
    command -v clang-18 >/dev/null
    command -v ld.lld-18 >/dev/null
    if [[ ! -d build/ndk-host ]]; then
        mkdir -p build
        cp -al "$ndk" build/ndk-host
        bin="$PWD/build/ndk-host/toolchains/llvm/prebuilt/linux-x86_64/bin"
        rm "$bin/clang" "$bin/clang++"
        cat > "$bin/clang" <<'CLANG'
#!/usr/bin/env bash
set -e
base="$(cd "$(dirname "$0")/.." && pwd)"
mode=()
[[ ${0##*/} == clang++ ]] && mode=(--driver-mode=g++)
exec /usr/bin/clang-18 "${mode[@]}" --sysroot="$base/sysroot" -resource-dir="$base/lib/clang/pane" --rtlib=compiler-rt --unwindlib=libunwind -fuse-ld=/usr/bin/ld.lld-18 -Wl,-z,max-page-size=16384 -Wno-unused-command-line-argument "$@"
CLANG
        chmod +x "$bin/clang"
        ln -s clang "$bin/clang++"
        for tool in llvm-ar llvm-ranlib llvm-strip llvm-objcopy; do
            if [[ -x /usr/bin/$tool-18 ]]; then rm "$bin/$tool"; ln -s "/usr/bin/$tool-18" "$bin/$tool"; fi
        done
    fi
    resource="$PWD/build/ndk-host/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/pane"
    mkdir -p "$resource"
    ln -sfn "$(clang-18 -print-resource-dir)/include" "$resource/include"
    ln -sfn ../21/lib "$resource/lib"
    ndk="$PWD/build/ndk-host"
fi
cmake -S vendor/FreeRDP/client/Android/Studio/freeRDPCore/src/main/cpp -B build/rdp \
    --toolchain "$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release \
    -DWITH_FFMPEG=OFF -DWITH_OPENH264=OFF -DWITH_OPUS=OFF -DWITH_WEBP=OFF \
    -DWITH_JPEG=OFF -DWITH_PNG=OFF -DWITH_CJSON=OFF -GNinja
cmake --build build/rdp -j 2
mkdir -p app/src/main/jniLibs/arm64-v8a
prefix=vendor/FreeRDP/client/Android/Studio/freeRDPCore/src/main/jniLibs/arm64-v8a/lib
for lib in libcrypto libssl liburiparser libwinpr3 libfreerdp3 libfreerdp-client3; do
    cp -L "$prefix/$lib.so" app/src/main/jniLibs/arm64-v8a/
done
cp build/rdp/libfreerdp-android.so app/src/main/jniLibs/arm64-v8a/
strip_tool="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
"$strip_tool" --strip-unneeded app/src/main/jniLibs/arm64-v8a/*.so
