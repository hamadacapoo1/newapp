package com.mohamed.voicenotes;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

public class RecordActivity extends BaseActivity {

    private static final int REQ_AUDIO = 10;

    private EditText etText;
    private TextView tvStatus, tvPartial;
    private Button btnToggle, btnSave;

    private SpeechService speechService;
    private Recognizer recognizer;
    private boolean listening = false;
    private boolean starting = false;
    private boolean active = false;
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

        Ui.rtl(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
        if (listening) stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listening) stopListening();
    }

    @Override
    public void onBackPressed() {
        stopThenSave(true);
    }

    // ---------------- التسجيل ----------------

    private void startListening() {
        if (starting || listening) return;

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }

        if (!ModelManager.isReady(this)) {
            Toast.makeText(this, "لازم تحمّل الموديل الأول", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }

        starting = true;
        btnToggle.setEnabled(false);
        tvStatus.setText("جاري تحميل الموديل... (أول مرة بياخد شوية)");

        new Thread(new Runnable() {
            public void run() {
                try {
                    Model m = ModelManager.load(RecordActivity.this);
                    final Recognizer r = new Recognizer(m, 16000.0f);
                    final SpeechService s = new SpeechService(r, 16000.0f);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            starting = false;
                            btnToggle.setEnabled(true);
                            if (!active) {
                                s.shutdown();
                                closeQuiet(r);
                                tvStatus.setText("متوقف.");
                                return;
                            }
                            recognizer = r;
                            speechService = s;
                            s.startListening(listener);
                            listening = true;
                            btnToggle.setText("⏹ إيقاف");
                            tvStatus.setText("🔴 بيسمعك دلوقتي... اتكلم");
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            starting = false;
                            btnToggle.setEnabled(true);
                            tvStatus.setText("خطأ: " + e);
                        }
                    });
                }
            }
        }).start();
    }

    private void stopListening() {
        if (speechService != null) {
            try {
                speechService.stop();
            } catch (Exception e) {
            }
            try {
                speechService.shutdown();
            } catch (Exception e) {
            }
            speechService = null;
        }
        if (recognizer != null) {
            closeQuiet(recognizer);
            recognizer = null;
        }
        listening = false;
        tvPartial.setText("");
        btnToggle.setText("🎙 ابدأ التسجيل");
        tvStatus.setText("متوقف.");
    }

    private void closeQuiet(Recognizer r) {
        try {
            r.close();
        } catch (Exception e) {
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {
        public void onPartialResult(String hypothesis) {
            final String t = extract(hypothesis, "partial");
            runOnUiThread(new Runnable() {
                public void run() {
                    tvPartial.setText(t);
                }
            });
        }

        public void onResult(String hypothesis) {
            appendFinal(extract(hypothesis, "text"));
        }

        public void onFinalResult(String hypothesis) {
            appendFinal(extract(hypothesis, "text"));
            runOnUiThread(new Runnable() {
                public void run() {
                    tvPartial.setText("");
                }
            });
        }

        public void onError(Exception e) {
            final String msg = String.valueOf(e);
            runOnUiThread(new Runnable() {
                public void run() {
                    tvStatus.setText("خطأ: " + msg);
                }
            });
        }

        public void onTimeout() {
        }
    };

    private String extract(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    private void appendFinal(final String text) {
        if (text == null || text.trim().length() == 0) return;
        runOnUiThread(new Runnable() {
            public void run() {
                if (etText.getText().length() > 0) etText.append(" ");
                etText.append(text.trim());
            }
        });
    }

    // ---------------- الحفظ ----------------

    private boolean saveNote() {
        String text = etText.getText().toString().trim();
        if (text.length() == 0) return false;
        db.insert(Ui.titleFrom(text), text);
        saved = true;
        return true;
    }

    /** بيوقف التسجيل، وبعد ما آخر نتيجة توصل بيحفظ. */
    private void stopThenSave(final boolean auto) {
        if (saved) {
            finish();
            return;
        }
        stopListening();
        etText.post(new Runnable() {
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
        });
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
