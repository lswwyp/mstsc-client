/*
 * FreeRDP JNI callbacks: native code calls getSession(inst) to get UIEventListener.
 * Mstsc app registers SessionState when connecting so graphics callbacks can run.
 */
package com.freerdp.freerdpcore.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalApp {
    private static final Map<Long, SessionState> sessionMap = new ConcurrentHashMap<>();

    public static SessionState getSession(long inst) {
        return sessionMap.get(inst);
    }

    public static void registerSession(long inst, SessionState session) {
        sessionMap.put(inst, session);
    }

    public static void unregisterSession(long inst) {
        sessionMap.remove(inst);
    }
}
