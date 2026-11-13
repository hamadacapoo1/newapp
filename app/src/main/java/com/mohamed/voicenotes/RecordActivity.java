package com.mohamed.voicenotes;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class RecordActivity extends BaseActivity {

    private static final int REQ_AUDIO = 10;

    private EditText etText;
    private TextView tvStatus, tvPartial;
    private Button btnToggle, btnSave, btnTranslate;
    private Spinner spEngine, spLang;

    private final List<String> codes = Langs.all();
    private Stt stt;
    private boolean listening = false;
    private boolean saved = false;

    private NotesDb db;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_record);
        db = new NotesDb(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvPartial = (TextView) findViewById(R.id.tvPartial);
        etText = (EditText) findViewById(R.id.etText);
        btnToggle = (Button) findViewById(R.id.btnToggle);
        btnSave = (Button) findViewById(R.id.btnSave);
        btnTranslate = (Button) findViewById(R.id.btnTranslate);
        spEngine = (Spinner) findViewById(R.id.spEngine);
        spLang = (Spinner) findViewById(R.id.spLang);

        // محرك الصوت
        List<String> eng = new ArrayList<String>();
        eng.add("🔹 " + Engine.label(Engine.DEVICE));
        eng.add("🔸 " + Engine.label(Engine.VOSK));
        ArrayAdapter<String> ae = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, eng);
        ae.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEngine.setAdapter(ae);
        spEngine.setSelection(Engine.VOSK.equals(Prefs.getEngine(this)) ? 1 : 0);
        spEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Prefs.setEngine(RecordActivity.this, pos == 1 ? Engine.VOSK : Engine.DEVICE);
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        // لغة الكلام
        List<String> names = new ArrayList<String>();
        for (String c : codes) names.add(Langs.name(c));
        ArrayAdapter<String> al = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names);
        al.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLang.setAdapter(al);
        spLang.setSelection(Langs.indexOf(Prefs.getSrc(this)));
        spLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Prefs.setSrc(RecordActivity.this, codes.get(pos));
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        btnToggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (listening) stopListening();
                else startListening();
            }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopThenSave(false);
            }
        });
        btnTranslate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TranslateDialog.show(RecordActivity.this, etText.getText().toString(),
                        new TranslateDialog.OnUse() {
                            public void use(String t, boolean replace) {
                                if (replace) {
                                    etText.setText(t);
                                } else {
                                    if (etText.getText().length() > 0) etText.append("\n\n");
                                    etText.append(t);
                                }
                            }
                        });
            }
        });

        Ui.rtl(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (listening) stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stt != null) stt.release();
    }

    @Override
    public void onBackPressed() {
        stopThenSave(true);
    }

    // ---------------- التسجيل ----------------

    private void setIdleUi(String status) {
        listening = false;
        spEngine.setEnabled(true);
        spLang.setEnabled(true);
        btnToggle.setText("🎙 ابدأ التسجيل");
        tvPartial.setText("");
        tvStatus.setText(status);
    }

    private void startListening() {
        if (listening) return;

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }

        String engine = Prefs.getEngine(this);
        String lang = Prefs.getSrc(this);

        if (Engine.VOSK.equals(engine)) {
            if (!"ar".equals(lang)) {
                Toast.makeText(this, "الموديل المحمّل عربي بس. اختار «محرك الجهاز» للغات التانية.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!ModelManager.isReady(this)) {
                Toast.makeText(this, "لازم تحمّل الموديل الأول", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, SetupActivity.class));
                return;
            }
        }

        if (stt != null) stt.release();
        stt = Engine.create(this, true, engine);
        listening = true;
        spEngine.setEnabled(false);
        spLang.setEnabled(false);
        btnToggle.setText("⏹ إيقاف");
        tvStatus.setText(Engine.VOSK.equals(engine)
                ? "جاري تحميل الموديل... (أول مرة بياخد شوية)"
                : "جاري تجهيز محرك الجهاز...");

        stt.start(lang, new Stt.Listener() {
            public void onReady() {
                if (listening) tvStatus.setText("🔴 بيسمعك دلوقتي... اتكلم");
            }

            public void onPartial(String text) {
                tvPartial.setText(text);
            }

            public void onFinal(String text) {
                tvPartial.setText("");
                appendFinal(text);
            }

            public void onError(String message) {
                setIdleUi("⚠ " + message);
            }
        });
    }

    private void stopListening() {
        if (stt != null) stt.stop();
        setIdleUi("متوقف.");
    }

    private void appendFinal(String text) {
        if (text == null || text.trim().length() == 0) return;
        if (etText.getText().length() > 0) etText.append(" ");
        etText.append(text.trim());
    }

    // ---------------- الحفظ ----------------

    private boolean saveNote() {
        String text = etText.getText().toString().trim();
        if (text.length() == 0) return false;
        db.insert(Ui.titleFrom(text), text);
        saved = true;
        return true;
    }

    /** بيوقف التسجيل، وبعد ما آخر نتيجة توصل بيحفظ (محرك الجهاز محتاج لحظة). */
    private void stopThenSave(final boolean auto) {
        if (saved) {
            finish();
            return;
        }
        boolean wasListening = listening;
        stopListening();
        etText.postDelayed(new Runnable() {
            public void run() {
                boolean ok = saveNote();
                if (ok) {
                    Toast.makeText(RecordActivity.this,
                            auto ? "تم حفظ النوت تلقائياً ✅" : "تم الحفظ ✅",
                            Toast.LENGTH_SHORT).show();
                    finish();
                } else if (auto) {
                    finish();
                } else {
                    Toast.makeText(RecordActivity.this, "مفيش كلام يتحفظ",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }, wasListening ? 900 : 0);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_AUDIO && res.length > 0
                && res[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            Toast.makeText(this, "لازم تسمح بالمايكروفون عشان التسجيل يشتغل",
                    Toast.LENGTH_LONG).show();
        }
    }
}
