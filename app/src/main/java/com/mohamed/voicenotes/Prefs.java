package com.mohamed.voicenotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** إعدادات التطبيق (الوضع الليلي). */
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
}
