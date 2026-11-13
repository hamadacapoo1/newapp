package com.mohamed.voicenotes;

import android.content.Context;

/** اختيار محرك الصوت: الجهاز نفسه أو الموديل اللي اتحمّل. */
public class Engine {

    public static final String DEVICE = "device";
    public static final String VOSK = "vosk";

    public static Stt create(Context c, boolean continuous, String engine) {
        if (VOSK.equals(engine)) return new VoskStt(c);
        return new DeviceStt(c, continuous);
    }

    public static String label(String engine) {
        return VOSK.equals(engine) ? "الموديل المحمّل (Vosk - عربي)" : "محرك الجهاز";
    }
}
