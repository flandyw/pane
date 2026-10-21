package com.freerdp.freerdpcore.services;

import android.content.Context;
import android.graphics.Bitmap;

/** Thin binding to FreeRDP 3.32.2's Android JNI ABI. */
public class LibFreeRDP {
    public interface Listener {
        void connected();
        void ended(boolean failed);
        void resized(int width, int height);
        void damaged(int x, int y, int width, int height);
        void pointer(int[] pixels, int width, int height, int hotX, int hotY);
        void clipboard(String text);
    }
    public static volatile Listener listener;
    static {
        System.loadLibrary("freerdp-android");
        System.loadLibrary("freerdp-client3");
        System.loadLibrary("freerdp3");
        System.loadLibrary("winpr3");
    }
    public static native long freerdp_new(Context context);
    public static native void freerdp_free(long instance);
    public static native boolean freerdp_parse_arguments(long instance, String[] args);
    public static native boolean freerdp_connect(long instance);
    public static native boolean freerdp_disconnect(long instance);
    public static native boolean freerdp_update_graphics(long instance, Bitmap bitmap, int x, int y, int width, int height);
    public static native boolean freerdp_send_cursor_event(long instance, int x, int y, int flags);
    public static native boolean freerdp_send_key_event(long instance, int keycode, boolean down);
    public static native boolean freerdp_send_unicodekey_event(long instance, int keycode, boolean down);
    public static native boolean freerdp_send_clipboard_data(long instance, String data);
    public static native String freerdp_get_last_error_string(long instance);
    public static native String freerdp_get_version();

    private static void OnPreConnect(long i) {}
    private static void OnConnectionSuccess(long i) { if (listener != null) listener.connected(); }
    private static void OnConnectionFailure(long i) { if (listener != null) listener.ended(true); }
    private static void OnDisconnecting(long i) {}
    private static void OnDisconnected(long i) { if (listener != null) listener.ended(false); }
    private static void OnSettingsChanged(long i, int w, int h, int bpp) { if (listener != null) listener.resized(w,h); }
    private static void OnGraphicsResize(long i, int w, int h, int bpp) { if (listener != null) listener.resized(w,h); }
    private static void OnGraphicsUpdate(long i, int x, int y, int w, int h) { if (listener != null) listener.damaged(x,y,w,h); }
    private static boolean OnAuthenticate(long i, StringBuilder u, StringBuilder d, StringBuilder p) { return false; }
    private static boolean OnGatewayAuthenticate(long i, StringBuilder u, StringBuilder d, StringBuilder p) { return false; }
    // Unknown or changed certificates fail closed; the profile supplies the verified SHA-256 pin.
    private static int OnVerifyCertificateEx(long i, String host, long port, String cn, String subject, String issuer, String fp, long flags) { return 0; }
    private static int OnVerifyChangedCertificateEx(long i, String host, long port, String cn, String subject, String issuer, String fp, String os, String oi, String of, long flags) { return 0; }
    private static boolean OnExperimentalFeature(long i, int feature) { return false; }
    private static void OnRemoteClipboardChanged(long i, String text) { if (listener != null) listener.clipboard(text); }
    private static void OnRemoteClipboardImageChanged(long i, byte[] data) {}
    private static void OnPointerSet(long i, int[] pixels, int w, int h, int x, int y) { if (listener != null) listener.pointer(pixels,w,h,x,y); }
    private static void OnPointerSetNull(long i) { if (listener != null) listener.pointer(null,0,0,0,0); }
    private static void OnPointerSetDefault(long i) { if (listener != null) listener.pointer(null,-1,-1,0,0); }
    private static void OnRailWindowUpdate(long i, long id, int w, int h, int[] pixels) {}
    private static void OnRailWindowMove(long i, long id, int x, int y, int w, int h) {}
    private static void OnRailWindowHide(long i, long id) {}
    private static void OnRailWindowDestroy(long i, long id) {}
    private static void OnRailSessionEnd(long i) {}
    private static void OnRailMonitoredDesktop(long i, long[] ids, long active) {}
}
