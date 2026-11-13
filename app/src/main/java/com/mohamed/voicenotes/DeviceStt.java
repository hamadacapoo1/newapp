package com.mohamed.voicenotes;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * تحويل الصوت لنص بإمكانيات الجهاز نفسه (SpeechRecognizer).
 * - أندرويد 13+ : بيستخدم المعرّف اللي شغال جوه الجهاز (أوفلاين بالكامل) لو متوفر.
 * - غير كده: بيطلب وضع الأوفلاين (EXTRA_PREFER_OFFLINE) من خدمة الصوت بتاعة الجهاز.
 * الأوفلاين بيحتاج إن حزمة اللغة تكون منزّلة في إعدادات الصوت بالجهاز.
 */
public class DeviceStt implements Stt {

    private final Context ctx;
    private final boolean continuous;
    private final Handler h = new Handler(Looper.getMainLooper());

    private SpeechRecognizer sr;
    private Listener l;
    private String lang = "en";
    private boolean running = false;
    private boolean onDevicePref;
    private boolean gotResult = false;
    private String lastPartial = "";
    private int errStreak = 0;

    private final Runnable restart = new Runnable() {
        public void run() {
            if (running) listen();
        }
    };

    private final Runnable flush = new Runnable() {
        public void run() {
            flushPartial();
            destroyRec();
        }
    };

    public DeviceStt(Context c, boolean continuous) {
        this.ctx = c.getApplicationContext();
        this.continuous = continuous;
        this.onDevicePref = Build.VERSION.SDK_INT >= 33;
    }

    public boolean isActive() {
        return running;
    }

    public void start(String langCode, Listener listener) {
        h.removeCallbacks(flush);
        h.removeCallbacks(restart);
        destroyRec();
        l = listener;
        lang = langCode;
        running = true;
        errStreak = 0;
        boolean avail = SpeechRecognizer.isRecognitionAvailable(ctx);
        if (!avail && Build.VERSION.SDK_INT >= 33) {
            avail = SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx);
        }
        if (!avail) {
            fail("جهازك مفيهوش خدمة تعرّف على الكلام. جرّب الموديل المحمّل بدلها.");
            return;
        }
        listen();
    }

    private void listen() {
        if (sr == null) {
            try {
                if (onDevicePref && Build.VERSION.SDK_INT >= 33
                        && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                    sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
                } else {
                    sr = SpeechRecognizer.createSpeechRecognizer(ctx);
                }
            } catch (Throwable e) {
                fail("مقدرتش أشغّل خدمة الصوت: " + e);
                return;
            }
            sr.setRecognitionListener(rl);
        }
        gotResult = false;
        lastPartial = "";
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String tag = Langs.sttTag(lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        try {
            sr.startListening(i);
        } catch (Throwable e) {
            fail("خطأ في بدء الاستماع: " + e);
        }
    }

    public void stop() {
        if (!running && sr == null) return;
        running = false;
        h.removeCallbacks(restart);
        if (sr != null) {
            try {
                sr.stopListening();
            } catch (Throwable e) {
            }
            h.removeCallbacks(flush);
            h.postDelayed(flush, 700);
        }
    }

    public void release() {
        h.removeCallbacks(restart);
        h.removeCallbacks(flush);
        running = false;
        destroyRec();
        l = null;
    }

    private void destroyRec() {
        if (sr != null) {
            try {
                sr.cancel();
            } catch (Throwable e) {
            }
            try {
                sr.destroy();
            } catch (Throwable e) {
            }
            sr = null;
        }
    }

    private void flushPartial() {
        if (!gotResult && lastPartial.trim().length() > 0 && l != null) {
            gotResult = true;
            String p = lastPartial;
            lastPartial = "";
            l.onFinal(p);
        }
    }

    private void fail(String msg) {
        running = false;
        destroyRec();
        if (l != null) l.onError(msg);
    }

    private final RecognitionListener rl = new RecognitionListener() {
        public void onReadyForSpeech(Bundle params) {
            if (l != null && running) l.onReady();
        }

        public void onBeginningOfSpeech() {
        }

        public void onRmsChanged(float rmsdB) {
        }

        public void onBufferReceived(byte[] buffer) {
        }

        public void onEndOfSpeech() {
        }

        public void onEvent(int eventType, Bundle params) {
        }

        public void onPartialResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) {
                lastPartial = r.get(0);
                if (l != null) l.onPartial(lastPartial);
            }
        }

        public void onResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String t = (r != null && !r.isEmpty()) ? r.get(0) : "";
            errStreak = 0;
            gotResult = true;
            if (t.trim().length() > 0 && l != null) l.onFinal(t);
            if (running && continuous) {
                h.postDelayed(restart, 200);
            } else {
                running = false;
                h.removeCallbacks(flush);
                h.postDelayed(flush, 300);
            }
        }

        public void onError(int e) {
            if (!running) {
                // اتوقفنا بإيدينا: لو فيه نص مؤقت نعتبره نهائي
                flushPartial();
                return;
            }
            // 6 = SPEECH_TIMEOUT , 7 = NO_MATCH
            if (e == 6 || e == 7) {
                if (continuous) {
                    h.postDelayed(restart, 200);
                } else {
                    fail("ما سمعتش كلام، جرّب تاني.");
                }
                return;
            }
            // 8 = RECOGNIZER_BUSY , 11 = SERVER_DISCONNECTED
            if (e == 8 || e == 11) {
                errStreak++;
                destroyRec();
                if (errStreak > 4) {
                    fail("خدمة الصوت مشغولة. اقفل أي تطبيق بيستخدم المايك وجرّب تاني.");
                } else {
                    h.postDelayed(restart, 600);
                }
                return;
            }
            // 12 = LANGUAGE_NOT_SUPPORTED , 13 = LANGUAGE_UNAVAILABLE
            if (e == 12 || e == 13) {
                if (onDevicePref && Build.VERSION.SDK_INT >= 33) {
                    onDevicePref = false;
                    destroyRec();
                    h.post(restart);
                } else {
                    fail("اللغة دي مش متاحة في خدمة الصوت بتاعة الجهاز. نزّل حزمتها من إعدادات الصوت بالجهاز.");
                }
                return;
            }
            if (e == 9) {
                fail("لازم تسمح بالمايكروفون.");
                return;
            }
            // 1,2,3,4 = مشاكل شبكة/سيرفر: غالباً حزمة اللغة الأوفلاين مش منزّلة
            if (e == 1 || e == 2 || e == 4) {
                fail("الجهاز محتاج نت أو حزمة اللغة الأوفلاين مش منزّلة. نزّلها من إعدادات الصوت بالجهاز.");
                return;
            }
            errStreak++;
            if (errStreak > 4) {
                fail("خطأ في التعرف على الكلام (كود " + e + ").");
            } else {
                destroyRec();
                h.postDelayed(restart, 400);
            }
        }
    };
}
