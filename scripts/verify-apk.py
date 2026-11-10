#!/usr/bin/env python3
"""Ensure the Voxl-only APK has no legacy FreeRDP runtime or assets."""
import pathlib
import sys
import zipfile

apk = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'app/build/outputs/apk/release/app-release.apk')
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    forbidden = ('freerdp', 'winpr', 'libssl.so', 'libcrypto.so', 'uriparser')
    assert not any(token in name.lower() for name in names for token in forbidden), 'Legacy RDP files in APK'
    assert 'assets/third-party/voxl-pocket-android-LICENSE' in names, 'Missing Voxl license'
    assert 'classes.dex' in names, 'Missing application code'
print('Voxl-only APK checks passed')
