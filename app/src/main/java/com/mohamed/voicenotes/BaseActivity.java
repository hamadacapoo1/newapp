package com.mohamed.voicenotes;

import android.app.Activity;
import android.os.Bundle;

/** كل الشاشات بتورّث منها علشان الوضع الليلي يشتغل في التطبيق كله. */
public class BaseActivity extends Activity {

    private boolean appliedDark;

    @Override
    protected void onCreate(Bundle b) {
        appliedDark = Prefs.isDark(this);
        setTheme(appliedDark
                ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light);
        super.onCreate(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // لو الوضع اتغيّر من شاشة تانية، نعيد رسم الشاشة دي
        if (Prefs.isDark(this) != appliedDark) recreate();
    }
}
