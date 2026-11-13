package com.mohamed.voicenotes;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class Ui {

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void rtl(Activity a) {
        a.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    public static Button button(Context c, String text, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams fillWeight() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    public static LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    /** أول 6 كلمات من النص كعنوان للنوت */
    public static String titleFrom(String text) {
        String t = text.trim().replace("\n", " ");
        String[] w = t.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length && i < 6; i++) {
            if (i > 0) sb.append(' ');
            sb.append(w[i]);
        }
        return sb.toString();
    }
}
