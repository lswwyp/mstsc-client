/*
 * Session state for FreeRDP JNI callbacks: holds instance, surface bitmap, and UIEventListener.
 */
package com.freerdp.freerdpcore.application;

import android.graphics.drawable.BitmapDrawable;

import com.freerdp.freerdpcore.services.LibFreeRDP;

public class SessionState {
    private final long instance;
    private BitmapDrawable surface;
    private LibFreeRDP.UIEventListener uiEventListener;

    public SessionState(long instance) {
        this.instance = instance;
    }

    public long getInstance() {
        return instance;
    }

    public LibFreeRDP.UIEventListener getUIEventListener() {
        return uiEventListener;
    }

    public void setUIEventListener(LibFreeRDP.UIEventListener listener) {
        this.uiEventListener = listener;
    }

    public BitmapDrawable getSurface() {
        return surface;
    }

    public void setSurface(BitmapDrawable surface) {
        this.surface = surface;
    }
}
