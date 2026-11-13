package com.mohamed.voicenotes;

import android.content.Context;

import org.vosk.Model;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** تحميل موديل Vosk العربي مرة واحدة وتحميله في الذاكرة. */
public class ModelManager {

    public static final String DEFAULT_URL =
            "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip";

    private static Model model;

    public interface Progress {
        void onProgress(String message, int percent);
    }

    public static File baseDir(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        return new File(d, "vosk_model");
    }

    private static boolean looksLikeModel(File d) {
        return new File(d, "am").isDirectory();
    }

    /** يرجّع مجلد الموديل الحقيقي (اللي جواه am) أو null */
    public static File findModelDir(Context c) {
        File base = baseDir(c);
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

    public static boolean isReady(Context c) {
        return findModelDir(c) != null;
    }

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

    public static void delete(Context c) {
        release();
        deleteRec(baseDir(c));
    }

    public static void download(Context c, String urlStr, Progress p) throws IOException {
        release();
        File base = baseDir(c);
        File parent = base.getParentFile();
        deleteRec(base);

        if (parent.getUsableSpace() < 1200L * 1024 * 1024) {
            throw new IOException("المساحة الفاضية أقل من 1.2 جيجا");
        }

        File zip = new File(parent, "model_download.zip");
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setConnectTimeout(20000);
            con.setReadTimeout(60000);
            con.connect();
            if (con.getResponseCode() != 200) {
                throw new IOException("فشل التحميل، الكود: " + con.getResponseCode());
            }
            long total = con.getContentLength();

            InputStream in = new BufferedInputStream(con.getInputStream());
            OutputStream out = new BufferedOutputStream(new FileOutputStream(zip));
            try {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                long lastMb = -1;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    long mb = done / (1024 * 1024);
                    if (mb != lastMb) {
                        lastMb = mb;
                        int pct = total > 0 ? (int) (done * 100 / total) : -1;
                        p.onProgress("جاري التحميل... " + mb + " MB", pct);
                    }
                }
            } finally {
                out.close();
                in.close();
            }

            p.onProgress("جاري فك الضغط...", -1);
            unzip(zip, base, p);
        } finally {
            zip.delete();
        }

        if (findModelDir(c) == null) {
            throw new IOException("الملف اتحمل بس شكله مش موديل Vosk صحيح");
        }
    }

    private static void unzip(File zip, File dest, Progress p) throws IOException {
        dest.mkdirs();
        String root = dest.getCanonicalPath() + File.separator;
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
        try {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            int count = 0;
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(dest, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) {
                    throw new IOException("ملف مضغوط غير آمن");
                }
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                f.getParentFile().mkdirs();
                OutputStream o = new BufferedOutputStream(new FileOutputStream(f));
                try {
                    int n;
                    while ((n = zin.read(buf)) > 0) o.write(buf, 0, n);
                } finally {
                    o.close();
                }
                count++;
                if (count % 5 == 0) p.onProgress("فك الضغط: " + count + " ملف", -1);
            }
        } finally {
            zin.close();
        }
    }

    private static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }
}
