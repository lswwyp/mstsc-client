/*
   Android FreeRDP JNI Wrapper (minimal for MstscClient)
   Original Copyright 2013 Thincast Technologies GmbH.
   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
*/
package com.freerdp.freerdpcore.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.freerdp.freerdpcore.application.GlobalApp;
import com.freerdp.freerdpcore.application.SessionState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibFreeRDP {

    private static final String TAG = "LibFreeRDP";
    private static EventListener listener;
    private static boolean mHasH264 = false;
    private static boolean sLoaded = false;

    private static final Map<Long, Boolean> mInstanceState = new HashMap<>();

    static {
        try {
            System.loadLibrary("freerdp-android");
            String version = freerdp_get_jni_version();
            String[] versions = version.split("[.-]");
            if (versions.length > 0) {
                System.loadLibrary("freerdp-client" + versions[0]);
                System.loadLibrary("freerdp" + versions[0]);
                System.loadLibrary("winpr" + versions[0]);
            }
            Pattern pattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*");
            Matcher matcher = pattern.matcher(version);
            if (!matcher.matches() || matcher.groupCount() < 3) {
                throw new RuntimeException("APK broken: native library version " + version);
            }
            int major = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
            int minor = Integer.parseInt(Objects.requireNonNull(matcher.group(2)));
            int patch = Integer.parseInt(Objects.requireNonNull(matcher.group(3)));
            if (major > 2) {
                mHasH264 = freerdp_has_h264();
            } else if (minor > 5) {
                mHasH264 = freerdp_has_h264();
            } else if ((minor == 5) && (patch >= 1)) {
                mHasH264 = freerdp_has_h264();
            }
            sLoaded = true;
            Log.i(TAG, "FreeRDP native lib loaded. H264=" + mHasH264);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load FreeRDP native library", e);
            sLoaded = false;
        }
    }

    public static boolean isLoaded() {
        return sLoaded;
    }

    public static boolean hasH264Support() {
        return mHasH264;
    }

    private static native boolean freerdp_has_h264();
    private static native String freerdp_get_jni_version();
    private static native String freerdp_get_version();
    private static native long freerdp_new(Context context);
    private static native void freerdp_free(long inst);
    private static native boolean freerdp_parse_arguments(long inst, String[] args);
    private static native boolean freerdp_connect(long inst);
    private static native boolean freerdp_disconnect(long inst);
    private static native boolean freerdp_update_graphics(long inst, Bitmap bitmap, int x, int y, int width, int height);
    private static native boolean freerdp_send_cursor_event(long inst, int x, int y, int flags);
    private static native boolean freerdp_send_key_event(long inst, int keycode, boolean down);
    private static native boolean freerdp_send_unicodekey_event(long inst, int keycode, boolean down);
    private static native String freerdp_get_last_error_string(long inst);

    public static void setEventListener(EventListener l) {
        listener = l;
    }

    public static long newInstance(Context context) {
        return freerdp_new(context);
    }

    public static void freeInstance(long inst) {
        synchronized (mInstanceState) {
            if (Boolean.TRUE.equals(mInstanceState.get(inst))) {
                freerdp_disconnect(inst);
            }
            while (Boolean.TRUE.equals(mInstanceState.get(inst))) {
                try {
                    mInstanceState.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        freerdp_free(inst);
    }

    public static boolean connect(long inst) {
        synchronized (mInstanceState) {
            if (Boolean.TRUE.equals(mInstanceState.get(inst))) {
                throw new RuntimeException("instance already connected");
            }
        }
        return freerdp_connect(inst);
    }

    public static boolean disconnect(long inst) {
        synchronized (mInstanceState) {
            if (Boolean.TRUE.equals(mInstanceState.get(inst))) {
                return freerdp_disconnect(inst);
            }
        }
        return true;
    }

    /**
     * Set connection from host/port/user/domain/password (no BookmarkBase).
     */
    public static boolean setConnectionInfoFromParams(Context context, long inst,
            String host, int port, String username, String domain, String password,
            int width, int height) {
        ArrayList<String> args = new ArrayList<>();
        args.add(TAG);
        args.add("/gdi:sw");
        args.add("/v:" + host);
        args.add("/port:" + port);
        if (username != null && !username.isEmpty()) args.add("/u:" + username);
        if (domain != null && !domain.isEmpty()) args.add("/d:" + domain);
        if (password != null && !password.isEmpty()) args.add("/p:" + password);
        args.add(String.format("/size:%dx%d", width, height));
        args.add("/bpp:32");
        args.add("/sec:nla");
        args.add("/gfx");
        args.add("/network:auto");
        args.add("-wallpaper");
        args.add("-themes");
        args.add("/clipboard");
        args.add("/kbd:unicode:on");
        args.add("/cert:ignore");
        args.add("/log-level:WARN");
        String[] arrayArgs = args.toArray(new String[0]);
        return freerdp_parse_arguments(inst, arrayArgs);
    }

    public static boolean updateGraphics(long inst, Bitmap bitmap, int x, int y, int width, int height) {
        return freerdp_update_graphics(inst, bitmap, x, y, width, height);
    }

    public static boolean sendCursorEvent(long inst, int x, int y, int flags) {
        return freerdp_send_cursor_event(inst, x, y, flags);
    }

    public static boolean sendKeyEvent(long inst, int keycode, boolean down) {
        return freerdp_send_key_event(inst, keycode, down);
    }

    public static boolean sendUnicodeKeyEvent(long inst, int keycode, boolean down) {
        return freerdp_send_unicodekey_event(inst, keycode, down);
    }

    public static String getLastErrorString(long inst) {
        return freerdp_get_last_error_string(inst);
    }

    public static String getVersion() {
        return freerdp_get_version();
    }

    // --- Callbacks from native (must keep names for JNI) ---
    private static void OnConnectionSuccess(long inst) {
        if (listener != null) listener.OnConnectionSuccess(inst);
        synchronized (mInstanceState) {
            mInstanceState.put(inst, true);
            mInstanceState.notifyAll();
        }
    }

    private static void OnConnectionFailure(long inst) {
        if (listener != null) listener.OnConnectionFailure(inst);
        synchronized (mInstanceState) {
            mInstanceState.remove(inst);
            mInstanceState.notifyAll();
        }
    }

    private static void OnPreConnect(long inst) {
        if (listener != null) listener.OnPreConnect(inst);
    }

    private static void OnDisconnecting(long inst) {
        if (listener != null) listener.OnDisconnecting(inst);
    }

    private static void OnDisconnected(long inst) {
        if (listener != null) listener.OnDisconnected(inst);
        synchronized (mInstanceState) {
            mInstanceState.remove(inst);
            mInstanceState.notifyAll();
        }
    }

    private static void OnSettingsChanged(long inst, int width, int height, int bpp) {
        SessionState s = GlobalApp.getSession(inst);
        if (s != null) {
            UIEventListener ui = s.getUIEventListener();
            if (ui != null) ui.OnSettingsChanged(width, height, bpp);
        }
    }

    private static boolean OnAuthenticate(long inst, StringBuilder username, StringBuilder domain, StringBuilder password) {
        SessionState s = GlobalApp.getSession(inst);
        if (s == null) return false;
        UIEventListener ui = s.getUIEventListener();
        return ui != null && ui.OnAuthenticate(username, domain, password);
    }

    private static boolean OnGatewayAuthenticate(long inst, StringBuilder username, StringBuilder domain, StringBuilder password) {
        SessionState s = GlobalApp.getSession(inst);
        if (s == null) return false;
        UIEventListener ui = s.getUIEventListener();
        return ui != null && ui.OnGatewayAuthenticate(username, domain, password);
    }

    private static int OnVerifyCertificateEx(long inst, String host, long port, String commonName, String subject, String issuer, String fingerprint, long flags) {
        SessionState s = GlobalApp.getSession(inst);
        if (s == null) return 0;
        UIEventListener ui = s.getUIEventListener();
        return ui != null ? ui.OnVerifiyCertificateEx(host, port, commonName, subject, issuer, fingerprint, flags) : 0;
    }

    private static int OnVerifyChangedCertificateEx(long inst, String host, long port, String commonName, String subject, String issuer, String fingerprint, String oldSubject, String oldIssuer, String oldFingerprint, long flags) {
        SessionState s = GlobalApp.getSession(inst);
        if (s == null) return 0;
        UIEventListener ui = s.getUIEventListener();
        return ui != null ? ui.OnVerifyChangedCertificateEx(host, port, commonName, subject, issuer, fingerprint, oldSubject, oldIssuer, oldFingerprint, flags) : 0;
    }

    private static void OnGraphicsUpdate(long inst, int x, int y, int width, int height) {
        SessionState s = GlobalApp.getSession(inst);
        if (s != null) {
            UIEventListener ui = s.getUIEventListener();
            if (ui != null) ui.OnGraphicsUpdate(x, y, width, height);
        }
    }

    private static void OnGraphicsResize(long inst, int width, int height, int bpp) {
        SessionState s = GlobalApp.getSession(inst);
        if (s != null) {
            UIEventListener ui = s.getUIEventListener();
            if (ui != null) ui.OnGraphicsResize(width, height, bpp);
        }
    }

    private static void OnRemoteClipboardChanged(long inst, String data) {
        SessionState s = GlobalApp.getSession(inst);
        if (s != null) {
            UIEventListener ui = s.getUIEventListener();
            if (ui != null) ui.OnRemoteClipboardChanged(data);
        }
    }

    public interface EventListener {
        void OnPreConnect(long instance);
        void OnConnectionSuccess(long instance);
        void OnConnectionFailure(long instance);
        void OnDisconnecting(long instance);
        void OnDisconnected(long instance);
    }

    public interface UIEventListener {
        void OnSettingsChanged(int width, int height, int bpp);
        boolean OnAuthenticate(StringBuilder username, StringBuilder domain, StringBuilder password);
        boolean OnGatewayAuthenticate(StringBuilder username, StringBuilder domain, StringBuilder password);
        int OnVerifiyCertificateEx(String host, long port, String commonName, String subject, String issuer, String fingerprint, long flags);
        int OnVerifyChangedCertificateEx(String host, long port, String commonName, String subject, String issuer, String fingerprint, String oldSubject, String oldIssuer, String oldFingerprint, long flags);
        void OnGraphicsUpdate(int x, int y, int width, int height);
        void OnGraphicsResize(int width, int height, int bpp);
        void OnRemoteClipboardChanged(String data);
    }
}
