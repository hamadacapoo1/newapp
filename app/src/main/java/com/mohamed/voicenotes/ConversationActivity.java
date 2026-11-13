package com.mohamed.voicenotes;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * محادثة فورية بين شخصين بلغتين مختلفتين.
 * اضغط زرارك واتكلم ← يتحوّل لنص ← يترجم لغة التاني ← (اختياري) يتنطق بصوت عالي.
 * وبنفس الطريقة بالعكس بزرار التاني.
 */
public class ConversationActivity extends BaseActivity {

    private static final int REQ_AUDIO = 21;

    private Spinner spMine, spTheirs, spEngine;
    private Button btnMine, btnTheirs, btnSwap;
    private TextView tvStatus;
    private LinearLayout log;
    private ScrollView sv;
    private CheckBox cbSpeak;

    private final List<String> codes = Langs.all();
    private String mine, theirs;
    private Stt stt;
    private boolean listening = false;
    private boolean listeningMine = false;
    private boolean pendingMine = true;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mine = Prefs.getMine(this);
        theirs = Prefs.getTheirs(this);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                ttsReady = (status == TextToSpeech.SUCCESS);
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));

        TextView title = new TextView(this);
        title.setText("💬 محادثة فورية");
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, Ui.matchWrap());

        List<String> names = new ArrayList<String>();
        for (String c : codes) names.add(Langs.name(c));

        // لغتي + لغة التاني + تبديل
        LinearLayout langs = new LinearLayout(this);
        langs.setOrientation(LinearLayout.HORIZONTAL);
        langs.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout c1 = new LinearLayout(this);
        c1.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText("أنا بتكلم:");
        c1.addView(t1);
        spMine = new Spinner(this);
        spMine.setAdapter(adapter(names));
        spMine.setSelection(Langs.indexOf(mine));
        c1.addView(spMine, Ui.matchWrap());
        langs.addView(c1, Ui.rowParams());

        btnSwap = Ui.button(this, "⇄", new View.OnClickListener() {
            public void onClick(View v) {
                if (listening) return;
                String t = mine;
                mine = theirs;
                theirs = t;
                spMine.setSelection(Langs.indexOf(mine));
                spTheirs.setSelection(Langs.indexOf(theirs));
                saveLangs();
                updateLabels();
            }
        });
        langs.addView(btnSwap);

        LinearLayout c2 = new LinearLayout(this);
        c2.setOrientation(LinearLayout.VERTICAL);
        TextView t2 = new TextView(this);
        t2.setText("هو/هي بيتكلم:");
        c2.addView(t2);
        spTheirs = new Spinner(this);
        spTheirs.setAdapter(adapter(names));
        spTheirs.setSelection(Langs.indexOf(theirs));
        c2.addView(spTheirs, Ui.matchWrap());
        langs.addView(c2, Ui.rowParams());
        root.addView(langs, Ui.matchWrap());

        // محرك الصوت
        TextView t3 = new TextView(this);
        t3.setText("محرك تحويل الصوت لنص:");
        root.addView(t3);
        spEngine = new Spinner(this);
        List<String> eng = new ArrayList<String>();
        eng.add(Engine.label(Engine.DEVICE));
        eng.add(Engine.label(Engine.VOSK));
        spEngine.setAdapter(adapter(eng));
        spEngine.setSelection(Engine.VOSK.equals(Prefs.getEngine(this)) ? 1 : 0);
        root.addView(spEngine, Ui.matchWrap());

        // سجل المحادثة
        sv = new ScrollView(this);
        log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        sv.addView(log, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(sv, Ui.fillWeight());

        tvStatus = new TextView(this);
        tvStatus.setTextSize(15);
        tvStatus.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        tvStatus.setText("اختار اللغتين واضغط زرارك واتكلم.");
        root.addView(tvStatus, Ui.matchWrap());

        cbSpeak = new CheckBox(this);
        cbSpeak.setText("نطق الترجمة بصوت عالي");
        cbSpeak.setChecked(Prefs.getSpeak(this));
        cbSpeak.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setSpeak(ConversationActivity.this, checked);
            }
        });
        root.addView(cbSpeak, Ui.matchWrap());

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btnMine = Ui.button(this, "", new View.OnClickListener() {
            public void onClick(View v) {
                toggle(true);
            }
        });
        btnTheirs = Ui.button(this, "", new View.OnClickListener() {
            public void onClick(View v) {
                toggle(false);
            }
        });
        btnMine.setMinHeight(Ui.dp(this, 80));
        btnTheirs.setMinHeight(Ui.dp(this, 80));
        btns.addView(btnMine, Ui.rowParams());
        btns.addView(btnTheirs, Ui.rowParams());
        root.addView(btns, Ui.matchWrap());

        setContentView(root);
        Ui.rtl(this);
        updateLabels();

        spMine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                mine = codes.get(pos);
                saveLangs();
                updateLabels();
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        spTheirs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                theirs = codes.get(pos);
                saveLangs();
                updateLabels();
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        spEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Prefs.setEngine(ConversationActivity.this, pos == 1 ? Engine.VOSK : Engine.DEVICE);
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        });
    }

    private ArrayAdapter<String> adapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private void saveLangs() {
        Prefs.setMine(this, mine);
        Prefs.setTheirs(this, theirs);
    }

    private void updateLabels() {
        if (listening) {
            btnMine.setText(listeningMine ? "⏹ إيقاف" : "🎙 أنا\n" + Langs.arName(mine));
            btnTheirs.setText(!listeningMine ? "⏹ إيقاف" : "🎙 هو/هي\n" + Langs.arName(theirs));
        } else {
            btnMine.setText("🎙 أنا\n" + Langs.arName(mine));
            btnTheirs.setText("🎙 هو/هي\n" + Langs.arName(theirs));
        }
        boolean idle = !listening;
        spMine.setEnabled(idle);
        spTheirs.setEnabled(idle);
        spEngine.setEnabled(idle);
        btnSwap.setEnabled(idle);
        btnMine.setEnabled(idle || listeningMine);
        btnTheirs.setEnabled(idle || !listeningMine);
    }

    // ---------------- الاستماع ----------------

    private void toggle(boolean isMine) {
        if (listening) {
            stopListening();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingMine = isMine;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startListening(isMine);
    }

    private void startListening(final boolean isMine) {
        final String from = isMine ? mine : theirs;
        final String to = isMine ? theirs : mine;
        if (from.equals(to)) {
            tvStatus.setText("اختار لغتين مختلفتين.");
            return;
        }
        String engine = Prefs.getEngine(this);
        if (Engine.VOSK.equals(engine) && !"ar".equals(from)) {
            tvStatus.setText("الموديل المحمّل عربي بس — اختار «محرك الجهاز» للغة " + Langs.arName(from) + ".");
            return;
        }
        if (stt != null) stt.release();
        stt = Engine.create(this, false, engine);
        listening = true;
        listeningMine = isMine;
        updateLabels();
        tvStatus.setText("⏳ جاري التجهيز...");
        final boolean[] handled = new boolean[]{false};
        stt.start(from, new Stt.Listener() {
            public void onReady() {
                if (listening) tvStatus.setText("🔴 بيسمعك... اتكلم بـ" + Langs.arName(from));
            }

            public void onPartial(String text) {
                if (listening && text.length() > 0) tvStatus.setText("🎤 " + text);
            }

            public void onFinal(String text) {
                if (handled[0] || text == null || text.trim().length() == 0) return;
                handled[0] = true;
                finishListening();
                handle(text.trim(), from, to, isMine);
            }

            public void onError(String message) {
                if (handled[0]) return;
                finishListening();
                tvStatus.setText("⚠ " + message);
            }
        });
    }

    /** نوقف الاستماع يدوي (آخر نتيجة لسه ممكن توصل). */
    private void stopListening() {
        if (stt != null) stt.stop();
        finishListening();
        tvStatus.setText("متوقف.");
    }

    private void finishListening() {
        listening = false;
        updateLabels();
    }

    // ---------------- الترجمة والعرض ----------------

    private void handle(String text, final String from, final String to, final boolean isMine) {
        tvStatus.setText("⏳ جاري الترجمة...");
        final TextView tr = addBubble(text, isMine);
        TranslateManager.translate(from, to, text, new TranslateManager.Cb() {
            public void ok(String r) {
                tr.setText(r);
                tvStatus.setText("جاهز.");
                scrollDown();
                if (cbSpeak.isChecked()) speak(r, to);
            }

            public void fail(String msg) {
                tr.setText("⚠ " + msg);
                tr.setTextColor(Color.rgb(220, 60, 60));
                tvStatus.setText("جاهز.");
                scrollDown();
            }
        });
    }

    /** بيضيف فقاعة كلام ويرجّع الـ TextView بتاع الترجمة علشان يتملى بعدين. */
    private TextView addBubble(String original, boolean isMine) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 10);
        bubble.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 14));
        bg.setColor(isMine ? Color.argb(70, 33, 150, 243) : Color.argb(70, 76, 175, 80));
        bubble.setBackground(bg);

        TextView o = new TextView(this);
        o.setText(original);
        o.setTextSize(13);
        o.setAlpha(0.75f);
        bubble.addView(o, Ui.matchWrap());

        TextView t = new TextView(this);
        t.setText("⏳");
        t.setTextSize(19);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextIsSelectable(true);
        bubble.addView(t, Ui.matchWrap());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = isMine ? Gravity.START : Gravity.END;
        lp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        log.addView(bubble, lp);
        scrollDown();
        return t;
    }

    private void scrollDown() {
        sv.post(new Runnable() {
            public void run() {
                sv.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void speak(String text, String langCode) {
        if (!ttsReady || tts == null) return;
        int r = tts.setLanguage(Langs.locale(langCode));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "صوت لغة " + Langs.arName(langCode)
                    + " مش متنزّل في الجهاز (إعدادات النطق)", Toast.LENGTH_LONG).show();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conv");
    }

    // ---------------- دورة الحياة ----------------

    @Override
    protected void onPause() {
        super.onPause();
        if (stt != null) {
            stt.release();
            stt = null;
        }
        if (listening) finishListening();
        if (tts != null) tts.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stt != null) stt.release();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_AUDIO && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            startListening(pendingMine);
        } else {
            Toast.makeText(this, "لازم تسمح بالمايكروفون", Toast.LENGTH_LONG).show();
        }
    }
}
