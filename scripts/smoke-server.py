#!/usr/bin/env python3
"""Test the local xrdp endpoint with a disposable account, never the user's password."""
import pathlib
import secrets
import subprocess

root=pathlib.Path(__file__).resolve().parent.parent
user='pane-smoke'
subprocess.run(['sudo','-n','useradd','--create-home','--shell','/bin/bash',user],check=True)
try:
    password=secrets.token_urlsafe(32)
    subprocess.run(['sudo','-n','chpasswd'],input=f'{user}:{password}\n',text=True,check=True)
    subprocess.run(['sudo','-n','-u',user,'sh','-c',"printf 'exec startxfce4\\n' > ~/.xsession"],check=True)
    pin=subprocess.check_output(['openssl','x509','-in','/etc/xrdp/cert.pem','-noout','-fingerprint','-sha256'],text=True).strip().split('=')[1].replace(':','')
    common=['xvfb-run','-a','timeout','25s','xfreerdp3','/v:127.0.0.1:3389',f'/u:{user}','/from-stdin:force','/auth-only','/sec:tls','/size:1600x1000','/bpp:24','/gdi:sw','/timeout:10000','/network:lan','/compression','/compression-level:2','-wallpaper','-window-drag','-menu-anims','-aero','+fonts','/audio-mode:2','/clipboard','/kbd:unicode:on','/log-level:INFO']
    for name,fingerprint in [('correct-pin',pin),('wrong-pin','00'*32)]:
        result=subprocess.run(common+[f'/cert:deny,fingerprint:sha256:{fingerprint}'],input=password+'\n',text=True,capture_output=True)
        (root/'build'/f'smoke-{name}.log').write_text(result.stdout+result.stderr)
        print(name,'exit',result.returncode)
        if name=='correct-pin':
            assert result.returncode==0, 'See build/smoke-correct-pin.log'
        else:
            assert result.returncode != 0 and 'ERRCONNECT_TLS_CONNECT_FAILED' in result.stdout+result.stderr, 'Mismatched certificate must fail TLS'
finally:
    subprocess.run(['sudo','-n','loginctl','terminate-user',user],capture_output=True)
    subprocess.run(['sudo','-n','userdel','--remove',user],check=True)
