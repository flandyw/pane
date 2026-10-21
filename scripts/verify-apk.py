#!/usr/bin/env python3
"""Assert ARM64 dependency closure, 16 KiB ELF/ZIP alignment and JNI exports."""
import pathlib
import re
import struct
import subprocess
import sys
import tempfile
import zipfile

native=pathlib.Path('vendor/FreeRDP/client/Android/Studio/freeRDPCore/src/main/cpp')
java=pathlib.Path('app/src/main/java/com/freerdp/freerdpcore/services/LibFreeRDP.java').read_text()
callbacks=set()
for file in native.glob('*.c'):
    callbacks.update(re.findall(r'freerdp_callback(?:_\w+)?\(\s*"(On\w+)"',file.read_text()))
assert callbacks <= set(re.findall(r'\b(On\w+)\(',java)), 'Missing native callback methods'

apk=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'app/build/outputs/apk/release/app-release.apk')
platform={'libc.so','libdl.so','libm.so','liblog.so','libz.so','libjnigraphics.so','libcamera2ndk.so','libmediandk.so','libaaudio.so','libOpenSLES.so'}
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as temp:
    libs={pathlib.PurePosixPath(n).name:n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')}
    required={'libcrypto.so','libssl.so','liburiparser.so','libwinpr3.so','libfreerdp3.so','libfreerdp-client3.so','libfreerdp-android.so'}
    assert required <= libs.keys(), f'Missing RDP libraries: {required-libs.keys()}'
    assert libs.keys() <= required|{'libandroidx.graphics.path.so'}, f'Unexpected libraries: {list(libs)}'
    for name,entry in libs.items():
        assert entry.startswith('lib/arm64-v8a/'), f'Unexpected ABI: {entry}'
        data=archive.read(entry)
        assert data[:5]==b'\x7fELF\x02' and struct.unpack_from('<H',data,18)[0]==183, f'Not ARM64: {name}'
        phoff=struct.unpack_from('<Q',data,32)[0]
        phsize,phnum=struct.unpack_from('<HH',data,54)
        for i in range(phnum):
            p=struct.unpack_from('<IIQQQQQQ',data,phoff+i*phsize)
            if p[0]==1:
                assert p[7]>=16384 and (p[2]-p[3])%16384==0, f'ELF alignment: {name}'
        info=archive.getinfo(entry)
        assert info.compress_type==zipfile.ZIP_STORED, f'Native library must be mmap-able: {name}'
        with apk.open('rb') as stream:
            stream.seek(info.header_offset+26)
            name_len,extra_len=struct.unpack('<HH',stream.read(4))
        assert (info.header_offset+30+name_len+extra_len)%16384==0, f'ZIP alignment: {name}'
        path=pathlib.Path(temp)/name
        path.write_bytes(data)
        dynamic=subprocess.check_output(['llvm-readelf-18','-d',str(path)],text=True)
        needed=set(re.findall(r'\(NEEDED\).*?\[(.*?)\]',dynamic))
        assert needed <= platform|libs.keys(), f'Missing libraries for {name}: {needed-platform-libs.keys()}'
        if name=='libfreerdp-android.so':
            exports=subprocess.check_output(['llvm-nm-18','--dynamic','--defined-only',str(path)],text=True)
            source=pathlib.Path('app/src/main/java/com/freerdp/freerdpcore/services/LibFreeRDP.java').read_text()
            for method in re.findall(r'public static native \w+ (\w+)\(',source):
                symbol='Java_com_freerdp_freerdpcore_services_LibFreeRDP_'+method.replace('_','_1')
                assert symbol in exports, f'Missing JNI symbol {symbol}'
        print(name,'ARM64, 16 KiB aligned, dependencies complete')
print('APK native checks passed')
