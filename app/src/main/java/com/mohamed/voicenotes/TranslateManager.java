package com.mohamed.voicenotes;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** الترجمة الأوفلاين (ML Kit): تنزيل/مسح حزم اللغات والترجمة نفسها. بعد تنزيل اللغة بتشتغل من غير نت. */
public class TranslateManager {

    public interface Cb {
        void ok(String result);

        void fail(String msg);
    }

    public interface DoneCb {
        void done(boolean ok, String msg);
    }

    public interface SetCb {
        void got(Set<String> codes);
    }

    private static final Map<String, Translator> cache = new HashMap<String, Translator>();

    private static synchronized Translator get(String src, String dst) {
        String k = src + ">" + dst;
        Translator t = cache.get(k);
        if (t == null) {
            TranslatorOptions o = new TranslatorOptions.Builder()
                    .setSourceLanguage(src)
                    .setTargetLanguage(dst)
                    .build();
            t = Translation.getClient(o);
            cache.put(k, t);
        }
        return t;
    }

    /** اللغات المنزّلة على الجهاز دلوقتي */
    public static void downloaded(final SetCb cb) {
        RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel.class)
                .addOnSuccessListener(new OnSuccessListener<Set<TranslateRemoteModel>>() {
                    public void onSuccess(Set<TranslateRemoteModel> s) {
                        Set<String> r = new HashSet<String>();
                        for (TranslateRemoteModel m : s) r.add(m.getLanguage());
                        cb.got(r);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    public void onFailure(Exception e) {
                        cb.got(new HashSet<String>());
                    }
                });
    }

    public static void download(String code, boolean wifiOnly, final DoneCb cb) {
        DownloadConditions.Builder b = new DownloadConditions.Builder();
        if (wifiOnly) b.requireWifi();
        TranslateRemoteModel m = new TranslateRemoteModel.Builder(code).build();
        RemoteModelManager.getInstance().download(m, b.build())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    public void onSuccess(Void v) {
                        cb.done(true, "");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    public void onFailure(Exception e) {
                        cb.done(false, "فشل التحميل — تأكد إنك على النت. " + e.getMessage());
                    }
                });
    }

    public static void delete(String code, final DoneCb cb) {
        TranslateRemoteModel m = new TranslateRemoteModel.Builder(code).build();
        RemoteModelManager.getInstance().deleteDownloadedModel(m)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    public void onSuccess(Void v) {
                        cb.done(true, "");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    public void onFailure(Exception e) {
                        cb.done(false, "فشل المسح: " + e.getMessage());
                    }
                });
    }

    /** ترجمة أوفلاين. بتفشل برسالة واضحة لو حزمة لغة مش منزّلة. */
    public static void translate(final String src, final String dst, final String text, final Cb cb) {
        if (src.equals(dst)) {
            cb.ok(text);
            return;
        }
        downloaded(new SetCb() {
            public void got(Set<String> have) {
                StringBuilder missing = new StringBuilder();
                if (!"en".equals(src) && !have.contains(src)) missing.append(Langs.arName(src));
                if (!"en".equals(dst) && !have.contains(dst)) {
                    if (missing.length() > 0) missing.append("، ");
                    missing.append(Langs.arName(dst));
                }
                if (missing.length() > 0) {
                    cb.fail("لازم تنزّل لغة: " + missing + " من شاشة «🌍 اللغات» الأول.");
                    return;
                }
                get(src, dst).translate(text)
                        .addOnSuccessListener(new OnSuccessListener<String>() {
                            public void onSuccess(String r) {
                                cb.ok(r);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            public void onFailure(Exception e) {
                                cb.fail("فشلت الترجمة: " + e.getMessage());
                            }
                        });
            }
        });
    }
}
