package com.mohamed.voicenotes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

/** التفريغ بالموديل العربي اللي اتحمّل (Vosk) — أوفلاين بالكامل، عربي بس. */
public class VoskStt implements Stt {

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechService service;
    private Recognizer rec;
    private boolean active = false;
    private int token = 0;

    public VoskStt(Context c) {
        this.ctx = c.getApplicationContext();
    }

    public boolean isActive() {
        return active;
    }

    public void start(String langCode, final Listener l) {
        if (active) return;
        if (!"ar".equals(langCode)) {
            l.onError("الموديل المحمّل عربي بس. للغات التانية اختار «محرك الجهاز».");
            return;
        }
        if (!ModelManager.isReady(ctx)) {
            l.onError("لازم تحمّل الموديل الأول من شاشة «الموديل».");
            return;
        }
        active = true;
        final int my = ++token;
        new Thread(new Runnable() {
            public void run() {
                try {
                    Model m = ModelManager.load(ctx);
                    final Recognizer r = new Recognizer(m, 16000.0f);
                    final SpeechService s = new SpeechService(r, 16000.0f);
                    main.post(new Runnable() {
                        public void run() {
                            if (!active || my != token) {
                                try {
                                    s.shutdown();
                                } catch (Exception e) {
                                }
                                closeQuiet(r);
                                return;
                            }
                            rec = r;
                            service = s;
                            s.startListening(wrap(l));
                            l.onReady();
                        }
                    });
                } catch (final Throwable e) {
                    main.post(new Runnable() {
                        public void run() {
                            if (my != token) return;
                            active = false;
                            l.onError("خطأ في الموديل: " + e);
                        }
                    });
                }
            }
        }).start();
    }

    private RecognitionListener wrap(final Listener l) {
        return new RecognitionListener() {
            public void onPartialResult(String h) {
                final String t = extract(h, "partial");
                main.post(new Runnable() {
                    public void run() {
                        l.onPartial(t);
                    }
                });
            }

            public void onResult(String h) {
                final String t = extract(h, "text");
                main.post(new Runnable() {
                    public void run() {
                        if (t.trim().length() > 0) l.onFinal(t);
                    }
                });
            }

            public void onFinalResult(String h) {
                final String t = extract(h, "text");
                main.post(new Runnable() {
                    public void run() {
                        if (t.trim().length() > 0) l.onFinal(t);
                    }
                });
            }

            public void onError(Exception e) {
                final String msg = String.valueOf(e);
                main.post(new Runnable() {
                    public void run() {
                        l.onError(msg);
                    }
                });
            }

            public void onTimeout() {
            }
        };
    }

    private String extract(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    public void stop() {
        active = false;
        token++;
        if (service != null) {
            try {
                service.stop();
            } catch (Exception e) {
            }
            try {
                service.shutdown();
            } catch (Exception e) {
            }
            service = null;
        }
        if (rec != null) {
            closeQuiet(rec);
            rec = null;
        }
    }

    public void release() {
        stop();
    }

    private void closeQuiet(Recognizer r) {
        try {
            r.close();
        } catch (Exception e) {
        }
    }
}
