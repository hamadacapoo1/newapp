package com.mohamed.voicenotes;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

/** إدارة حزم اللغات الأوفلاين: تنزيل / مسح لكل لغة. */
public class LanguagesActivity extends BaseActivity {

    private LinearLayout box;
    private TextView tvCount;
    private Set<String> have = new HashSet<String>();
    private final Set<String> busy = new HashSet<String>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        TextView title = new TextView(this);
        title.setText("🌍 لغات الترجمة الأوفلاين");
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, Ui.matchWrap());

        TextView info = new TextView(this);
        info.setText("نزّل اللغات اللي محتاجها مرة واحدة (حوالي 30 ميجا للغة) وبعدها الترجمة بتشتغل من غير نت. "
                + "الإنجليزية مدمجة.\n\n"
                + "ملحوظة: ده خاص بالترجمة بس. لو بتستخدم «محرك الجهاز» لتحويل الصوت لنص أوفلاين، "
                + "حزمة اللغة الصوتية بتتنزّل من إعدادات الصوت بالجهاز نفسه.");
        info.setTextSize(14);
        info.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        root.addView(info, Ui.matchWrap());

        CheckBox cbWifi = new CheckBox(this);
        cbWifi.setText("التحميل على الواي فاي بس");
        cbWifi.setChecked(Prefs.getWifiOnly(this));
        cbWifi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setWifiOnly(LanguagesActivity.this, checked);
            }
        });
        root.addView(cbWifi, Ui.matchWrap());

        root.addView(Ui.button(this, "🎤 إعدادات الصوت بالجهاز (حزم التعرف على الكلام)",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            startActivity(new Intent("android.settings.VOICE_INPUT_SETTINGS"));
                        } catch (Exception e) {
                            try {
                                startActivity(new Intent(Settings.ACTION_SETTINGS));
                            } catch (Exception e2) {
                                Toast.makeText(LanguagesActivity.this, "مقدرتش أفتح الإعدادات",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }), Ui.matchWrap());

        tvCount = new TextView(this);
        tvCount.setTextSize(15);
        tvCount.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        root.addView(tvCount, Ui.matchWrap());

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box, Ui.matchWrap());

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        Ui.rtl(this);
        reload();
    }

    private void reload() {
        TranslateManager.downloaded(new TranslateManager.SetCb() {
            public void got(Set<String> s) {
                have = s;
                render();
            }
        });
    }

    private void render() {
        box.removeAllViews();
        int n = 0;
        for (String code : Langs.all()) {
            if (!"en".equals(code) && have.contains(code)) n++;
        }
        tvCount.setText("اللغات المنزّلة: " + n);

        for (final String code : Langs.all()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));

            TextView name = new TextView(this);
            name.setText(Langs.name(code));
            name.setTextSize(16);
            row.addView(name, Ui.rowParams());

            if ("en".equals(code)) {
                TextView t = new TextView(this);
                t.setText("✅ مدمجة");
                row.addView(t);
            } else if (busy.contains(code)) {
                Button bt = new Button(this);
                bt.setText("⏳ جاري...");
                bt.setEnabled(false);
                row.addView(bt);
            } else if (have.contains(code)) {
                Button bt = Ui.button(this, "🗑 مسح", new View.OnClickListener() {
                    public void onClick(View v) {
                        confirmDelete(code);
                    }
                });
                row.addView(bt);
            } else {
                Button bt = Ui.button(this, "⬇ تحميل", new View.OnClickListener() {
                    public void onClick(View v) {
                        startDownload(code);
                    }
                });
                row.addView(bt);
            }
            box.addView(row, Ui.matchWrap());
        }
    }

    private void startDownload(final String code) {
        busy.add(code);
        render();
        TranslateManager.download(code, Prefs.getWifiOnly(this), new TranslateManager.DoneCb() {
            public void done(boolean ok, String msg) {
                busy.remove(code);
                if (!ok) Toast.makeText(LanguagesActivity.this, msg, Toast.LENGTH_LONG).show();
                reload();
            }
        });
    }

    private void confirmDelete(final String code) {
        new AlertDialog.Builder(this)
                .setMessage("تمسح لغة " + Langs.arName(code) + " من الجهاز؟")
                .setPositiveButton("مسح", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        busy.add(code);
                        render();
                        TranslateManager.delete(code, new TranslateManager.DoneCb() {
                            public void done(boolean ok, String msg) {
                                busy.remove(code);
                                if (!ok) Toast.makeText(LanguagesActivity.this, msg,
                                        Toast.LENGTH_LONG).show();
                                reload();
                            }
                        });
                    }
                })
                .setNegativeButton("لأ", null)
                .show();
    }
}
