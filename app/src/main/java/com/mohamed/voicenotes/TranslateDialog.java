package com.mohamed.voicenotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** نافذة ترجمة نص (أوفلاين) من لغة للغة، مع نسخ/استبدال/إضافة/حفظ كنوت جديد. */
public class TranslateDialog {

    public interface OnUse {
        void use(String translated, boolean replace);
    }

    public static void show(final Activity a, final String text, final OnUse onUse) {
        if (text == null || text.trim().length() == 0) {
            Toast.makeText(a, "مفيش نص يتترجم", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> codes = Langs.all();
        List<String> names = new ArrayList<String>();
        for (String c : codes) names.add(Langs.name(c));

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView l1 = new TextView(a);
        l1.setText("من لغة:");
        root.addView(l1);
        final Spinner spFrom = new Spinner(a);
        ArrayAdapter<String> ad1 = new ArrayAdapter<String>(a, android.R.layout.simple_spinner_item, names);
        ad1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFrom.setAdapter(ad1);
        spFrom.setSelection(Langs.indexOf(Prefs.getSrc(a)));
        root.addView(spFrom, Ui.matchWrap());

        TextView l2 = new TextView(a);
        l2.setText("إلى لغة:");
        root.addView(l2);
        final Spinner spTo = new Spinner(a);
        ArrayAdapter<String> ad2 = new ArrayAdapter<String>(a, android.R.layout.simple_spinner_item, names);
        ad2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTo.setAdapter(ad2);
        spTo.setSelection(Langs.indexOf(Prefs.getDst(a)));
        root.addView(spTo, Ui.matchWrap());

        final TextView tvOut = new TextView(a);
        tvOut.setTextSize(18);
        tvOut.setTextIsSelectable(true);
        tvOut.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));

        final String[] result = new String[]{""};

        Button btn = new Button(a);
        btn.setText("🌐 ترجم");
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String from = codes.get(spFrom.getSelectedItemPosition());
                String to = codes.get(spTo.getSelectedItemPosition());
                Prefs.setSrc(a, from);
                Prefs.setDst(a, to);
                tvOut.setText("⏳ جاري الترجمة...");
                result[0] = "";
                TranslateManager.translate(from, to, text, new TranslateManager.Cb() {
                    public void ok(String r) {
                        result[0] = r;
                        tvOut.setText(r);
                    }

                    public void fail(String msg) {
                        tvOut.setText("⚠ " + msg);
                    }
                });
            }
        });
        root.addView(btn, Ui.matchWrap());
        root.addView(tvOut, Ui.matchWrap());

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(small(a, "📋 نسخ", new View.OnClickListener() {
            public void onClick(View v) {
                if (result[0].length() == 0) return;
                ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("translation", result[0]));
                Toast.makeText(a, "تم النسخ", Toast.LENGTH_SHORT).show();
            }
        }), Ui.rowParams());
        row.addView(small(a, "💾 نوت جديد", new View.OnClickListener() {
            public void onClick(View v) {
                if (result[0].length() == 0) return;
                new NotesDb(a).insert(Ui.titleFrom(result[0]), result[0]);
                Toast.makeText(a, "اتحفظت كنوت جديد ✅", Toast.LENGTH_SHORT).show();
            }
        }), Ui.rowParams());
        root.addView(row, Ui.matchWrap());

        if (onUse != null) {
            LinearLayout row2 = new LinearLayout(a);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.addView(small(a, "🔁 استبدل النص", new View.OnClickListener() {
                public void onClick(View v) {
                    if (result[0].length() == 0) return;
                    onUse.use(result[0], true);
                    Toast.makeText(a, "تم", Toast.LENGTH_SHORT).show();
                }
            }), Ui.rowParams());
            row2.addView(small(a, "➕ أضفه تحت", new View.OnClickListener() {
                public void onClick(View v) {
                    if (result[0].length() == 0) return;
                    onUse.use(result[0], false);
                    Toast.makeText(a, "تم", Toast.LENGTH_SHORT).show();
                }
            }), Ui.rowParams());
            root.addView(row2, Ui.matchWrap());
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(root);
        new AlertDialog.Builder(a)
                .setTitle("🌐 ترجمة أوفلاين")
                .setView(sv)
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private static Button small(Context c, String t, View.OnClickListener l) {
        Button b = Ui.button(c, t, l);
        b.setTextSize(13);
        return b;
    }
}
