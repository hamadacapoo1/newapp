package com.mohamed.voicenotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** إعدادات التطبيق (الوضع الليلي، محرك الصوت، اللغات). */
public class Prefs {

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
    }

    /** لو المستخدم ماختارش حاجة، بنمشي على وضع الموبايل نفسه. */
    public static boolean isDark(Context c) {
        SharedPreferences p = sp(c);
        if (p.contains("dark")) return p.getBoolean("dark", false);
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void setDark(Context c, boolean dark) {
        sp(c).edit().putBoolean("dark", dark).commit();
    }

    // ---------------- محرك الصوت ----------------

    /** لو ماختارش: الموديل لو متحمّل، وإلا محرك الجهاز. */
    public static String getEngine(Context c) {
        SharedPreferences p = sp(c);
        if (p.contains("engine")) return p.getString("engine", Engine.DEVICE);
        return ModelManager.isReady(c) ? Engine.VOSK : Engine.DEVICE;
    }

    public static void setEngine(Context c, String e) {
        sp(c).edit().putString("engine", e).commit();
    }

    // ---------------- اللغات ----------------

    /** لغة الكلام في التسجيل / لغة النص في الترجمة */
    public static String getSrc(Context c) {
        return sp(c).getString("src", "ar");
    }

    public static void setSrc(Context c, String v) {
        sp(c).edit().putString("src", v).commit();
    }

    public static String getDst(Context c) {
        return sp(c).getString("dst", "en");
    }

    public static void setDst(Context c, String v) {
        sp(c).edit().putString("dst", v).commit();
    }

    /** المحادثة الفورية: لغتي / لغة التاني */
    public static String getMine(Context c) {
        return sp(c).getString("conv_mine", "ar");
    }

    public static void setMine(Context c, String v) {
        sp(c).edit().putString("conv_mine", v).commit();
    }

    public static String getTheirs(Context c) {
        return sp(c).getString("conv_theirs", "en");
    }

    public static void setTheirs(Context c, String v) {
        sp(c).edit().putString("conv_theirs", v).commit();
    }

    public static boolean getSpeak(Context c) {
        return sp(c).getBoolean("conv_speak", true);
    }

    public static void setSpeak(Context c, boolean v) {
        sp(c).edit().putBoolean("conv_speak", v).commit();
    }

    public static boolean getWifiOnly(Context c) {
        return sp(c).getBoolean("langs_wifi", false);
    }

    public static void setWifiOnly(Context c, boolean v) {
        sp(c).edit().putBoolean("langs_wifi", v).commit();
    }
}
