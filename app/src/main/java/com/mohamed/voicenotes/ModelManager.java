package com.mohamed.voicenotes;

import android.content.Context;
import android.content.SharedPreferences;

import org.vosk.Model;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** إدارة موديل Vosk العربي: مكانه، حالته، وتحميله في الذاكرة. التحميل نفسه بيتم في ModelDownloadService. */
public class ModelManager {

    public static final String DEFAULT_URL =
            "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip";

    public static final String S_IDLE = "idle";
    public static final String S_DOWNLOADING = "downloading";
    public static final String S_PAUSED = "paused";
    public static final String S_UNZIPPING = "unzipping";
    public static final String S_ERROR = "error";

    private static Model model;

    // ---------------------------------------------------------------- أماكن الملفات

    /** المكان اللي هيتحمل فيه الموديل */
    public static File baseDir(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        return new File(d, "vosk_model");
    }

    /** كل الأماكن اللي ممكن يكون الموديل موجود فيها (علشان نكتشف لو متحمل قبل كده) */
    private static File[] candidates(Context c) {
        File ext = c.getExternalFilesDir(null);
        File in = c.getFilesDir();
        if (ext != null) {
            return new File[]{new File(ext, "vosk_model"), new File(in, "vosk_model")};
        }
        return new File[]{new File(in, "vosk_model")};
    }

    /** الملف المؤقت اللي بيتحمل فيه الـ zip (بيفضل موجود لو التحميل اتوقف) */
    public static File partFile(Context c) {
        return new File(baseDir(c).getParentFile(), "model_download.part");
    }

    /** مجلد مؤقت لفك الضغط */
    public static File tmpDir(Context c) {
        return new File(baseDir(c).getParentFile(), "vosk_model_tmp");
    }

    private static boolean looksLikeModel(File d) {
        return new File(d, "am").isDirectory();
    }

    private static File findIn(File base) {
        if (!base.isDirectory()) return null;
        if (looksLikeModel(base)) return base;
        File[] kids = base.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (k.isDirectory() && looksLikeModel(k)) return k;
            }
        }
        return null;
    }

    /** يرجّع مجلد الموديل الحقيقي (اللي جواه am) أو null */
    public static File findModelDir(Context c) {
        File[] cands = candidates(c);
        for (int i = 0; i < cands.length; i++) {
            File f = findIn(cands[i]);
            if (f != null) return f;
        }
        return null;
    }

    public static boolean isReady(Context c) {
        return findModelDir(c) != null;
    }

    // ---------------------------------------------------------------- تحميل الموديل في الذاكرة

    public static synchronized Model load(Context c) throws IOException {
        if (model == null) {
            File d = findModelDir(c);
            if (d == null) throw new IOException("الموديل مش موجود");
            model = new Model(d.getAbsolutePath());
        }
        return model;
    }

    public static synchronized void release() {
        if (model != null) {
            try {
                model.close();
            } catch (Exception e) {
            }
            model = null;
        }
    }

    /** مسح الموديل بالكامل + أي تحميل ناقص */
    public static void delete(Context c) {
        release();
        File[] cands = candidates(c);
        for (int i = 0; i < cands.length; i++) deleteRec(cands[i]);
        cleanup(c);
    }

    /** مسح التحميل الناقص ورجوع الحالة لـ idle */
    public static void cleanup(Context c) {
        deleteRec(partFile(c));
        deleteRec(tmpDir(c));
        prefs(c).edit()
                .putString("state", S_IDLE)
                .putLong("total", 0)
                .putBoolean("user_paused", false)
                .putString("error", "")
                .commit();
    }

    // ---------------------------------------------------------------- حالة التحميل (بتتحفظ علشان تفضل بعد قفل التطبيق)

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("model_dl", Context.MODE_PRIVATE);
    }

    public static String getState(Context c) {
        return prefs(c).getString("state", S_IDLE);
    }

    public static void setState(Context c, String s) {
        prefs(c).edit().putString("state", s).commit();
    }

    public static String getUrl(Context c) {
        return prefs(c).getString("url", DEFAULT_URL);
    }

    public static void setUrl(Context c, String u) {
        prefs(c).edit().putString("url", u).commit();
    }

    public static long getTotal(Context c) {
        return prefs(c).getLong("total", 0);
    }

    public static void setTotal(Context c, long t) {
        prefs(c).edit().putLong("total", t).commit();
    }

    public static boolean isUserPaused(Context c) {
        return prefs(c).getBoolean("user_paused", false);
    }

    public static void setUserPaused(Context c, boolean v) {
        prefs(c).edit().putBoolean("user_paused", v).commit();
    }

    public static String getError(Context c) {
        return prefs(c).getString("error", "");
    }

    public static void setError(Context c, String e) {
        prefs(c).edit().putString("error", e == null ? "" : e).commit();
    }

    /** تحميل جديد من الصفر */
    public static void beginFresh(Context c, String url) {
        deleteRec(partFile(c));
        deleteRec(tmpDir(c));
        prefs(c).edit()
                .putString("url", url)
                .putLong("total", 0)
                .putString("state", S_DOWNLOADING)
                .putBoolean("user_paused", false)
                .putString("error", "")
                .commit();
    }

    public static long partialBytes(Context c) {
        File f = partFile(c);
        return f.exists() ? f.length() : 0;
    }

    // ---------------------------------------------------------------- أدوات

    public static String fmtSize(long bytes) {
        if (bytes < 0) bytes = 0;
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024) {
            return String.format(Locale.US, "%.2f GB", mb / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", mb);
    }

    public static String fmtTime(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    public static void deleteRec(File f) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }
}
