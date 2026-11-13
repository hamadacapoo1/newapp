package com.mohamed.voicenotes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

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

/** خدمة تحميل الموديل: شغالة في الخلفية، بإشعار، وتقدر توقفها مؤقتاً وتكمل. */
public class ModelDownloadService extends Service {

    public static final String ACTION_START = "com.mohamed.voicenotes.DL_START";
    public static final String ACTION_RESUME = "com.mohamed.voicenotes.DL_RESUME";
    public static final String ACTION_PAUSE = "com.mohamed.voicenotes.DL_PAUSE";
    public static final String ACTION_CANCEL = "com.mohamed.voicenotes.DL_CANCEL";

    static final String CHANNEL_ID = "model_download";
    static final int NOTIF_ID = 4101;
    private static final long MIN_FREE = 1200L * 1024 * 1024;

    private static final int END_DONE = 1;
    private static final int END_PAUSED = 2;
    private static final int END_ERROR = 3;
    private static final int END_CANCELLED = 4;
    private static final int END_INTERRUPTED = 5;

    private static volatile boolean running = false;
    /** تفاصيل فك الضغط عشان الشاشة تعرضها */
    public static volatile String detail = "";

    public static boolean isRunning() {
        return running;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;
    private volatile boolean pauseReq, cancelReq, destroyed;
    private volatile long speed = 0;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    // ---------------------------------------------------------------- تشغيل من برّه

    public static void send(Context c, String action) {
        Intent i = new Intent(c, ModelDownloadService.class);
        i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    // ---------------------------------------------------------------- دورة حياة الخدمة

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        enterForeground(buildProgress(this, ModelManager.partialBytes(this),
                ModelManager.getTotal(this), 0, "جاري التحضير..."));

        if (action == null) {
            // أندرويد رجّع الخدمة بعد ما اتقفلت من النظام
            if (ModelManager.S_DOWNLOADING.equals(ModelManager.getState(this))
                    && !ModelManager.isUserPaused(this) && !ModelManager.isReady(this)) {
                begin();
            } else {
                stopForeground(true);
                stopSelf();
            }
            return START_STICKY;
        }

        if (ACTION_START.equals(action) || ACTION_RESUME.equals(action)) {
            if (ModelManager.isReady(this)) {
                ModelManager.cleanup(this);
                stopForeground(true);
                stopSelf();
            } else if (worker == null) {
                begin();
            }
        } else if (ACTION_PAUSE.equals(action)) {
            if (worker != null) {
                pauseReq = true;
            } else {
                handleEnd(END_PAUSED);
            }
        } else if (ACTION_CANCEL.equals(action)) {
            if (worker != null) {
                cancelReq = true;
            } else {
                handleEnd(END_CANCELLED);
            }
        }
        return START_STICKY;
    }

    private void enterForeground(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            // لو النظام منع الإشعار الدائم هنكمل كخدمة عادية
        }
    }

    // ---------------------------------------------------------------- الشغل الفعلي

    private void begin() {
        pauseReq = false;
        cancelReq = false;
        ModelManager.setUserPaused(this, false);
        ModelManager.setState(this, ModelManager.S_DOWNLOADING);
        ModelManager.setError(this, "");
        acquireLocks();
        worker = new Thread(new Runnable() {
            public void run() {
                int r;
                try {
                    r = doWork();
                } catch (Throwable t) {
                    String m = t.getMessage();
                    ModelManager.setError(ModelDownloadService.this, m == null ? t.toString() : m);
                    r = END_ERROR;
                }
                final int fr = r;
                main.post(new Runnable() {
                    public void run() {
                        handleEnd(fr);
                    }
                });
            }
        });
        worker.start();
    }

    private int doWork() throws Exception {
        Context c = this;
        String url = ModelManager.getUrl(c);
        File part = ModelManager.partFile(c);
        File parent = part.getParentFile();
        parent.mkdirs();
        ModelManager.release();

        int failures = 0;
        boolean complete = false;
        while (!complete) {
            if (cancelReq) return END_CANCELLED;
            if (pauseReq) return END_PAUSED;
            if (destroyed) return END_INTERRUPTED;

            long done = part.exists() ? part.length() : 0;
            long total = ModelManager.getTotal(c);
            if (total > 0 && done > total) {
                part.delete();
                continue;
            }
            if (total > 0 && done == total) {
                complete = true;
                break;
            }
            if (parent.getUsableSpace() + done < MIN_FREE) {
                throw new IOException("المساحة الفاضية أقل من 1.2 جيجا");
            }
            try {
                int r = transfer(url, part);
                if (r != 0) return r;
                complete = true;
            } catch (IOException e) {
                if (cancelReq) return END_CANCELLED;
                if (pauseReq) return END_PAUSED;
                if (destroyed) return END_INTERRUPTED;
                long now = part.exists() ? part.length() : 0;
                if (now > done) failures = 0;
                else failures++;
                if (failures >= 5) throw e;
                notifyMsg("النت اتقطع، بحاول تاني...");
                for (int i = 0; i < 30; i++) {
                    if (cancelReq || pauseReq || destroyed) break;
                    Thread.sleep(100);
                }
            }
        }

        long total = ModelManager.getTotal(c);
        if (total > 0 && part.length() != total) {
            part.delete();
            throw new IOException("حجم الملف غير مطابق، ابدأ التحميل تاني");
        }

        // فك الضغط في مجلد مؤقت ثم نقله مكان الموديل
        ModelManager.setState(c, ModelManager.S_UNZIPPING);
        detail = "جاري فك الضغط...";
        notifyUnzip("جاري فك الضغط...");
        File tmp = ModelManager.tmpDir(c);
        ModelManager.deleteRec(tmp);
        int r = unzip(part, tmp);
        if (r != 0) {
            if (r != END_INTERRUPTED) ModelManager.deleteRec(tmp);
            return r;
        }
        File base = ModelManager.baseDir(c);
        ModelManager.deleteRec(base);
        if (!tmp.renameTo(base)) {
            ModelManager.deleteRec(tmp);
            throw new IOException("معرفتش أنقل الموديل لمكانه");
        }
        if (ModelManager.findModelDir(c) == null) {
            ModelManager.deleteRec(base);
            part.delete();
            ModelManager.setTotal(c, 0);
            throw new IOException("الملف اتحمل بس شكله مش موديل Vosk صحيح");
        }
        part.delete();
        return END_DONE;
    }

    /** بيرجّع 0 لو الملف اكتمل، أو END_* لو اتوقف. بيرمي IOException لو حصل خطأ شبكة. */
    private int transfer(String urlStr, File part) throws IOException {
        Context c = this;
        long done = part.exists() ? part.length() : 0;

        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(15000);
        con.setRequestProperty("Accept-Encoding", "identity");
        if (done > 0) con.setRequestProperty("Range", "bytes=" + done + "-");

        InputStream in = null;
        OutputStream out = null;
        try {
            con.connect();
            int code = con.getResponseCode();
            boolean append;
            long total;
            if (code == 206) {
                append = true;
                total = -1;
                String cr = con.getHeaderField("Content-Range");
                if (cr != null && cr.indexOf('/') > 0) {
                    total = parseLen(cr.substring(cr.indexOf('/') + 1));
                }
                if (total <= 0) {
                    long len = parseLen(con.getHeaderField("Content-Length"));
                    total = len > 0 ? done + len : ModelManager.getTotal(c);
                }
            } else if (code == 200) {
                append = false;
                done = 0;
                total = parseLen(con.getHeaderField("Content-Length"));
            } else if (code == 416) {
                part.delete();
                throw new IOException("السيرفر رفض المتابعة، هبدأ من الأول");
            } else {
                throw new IOException("فشل التحميل، الكود: " + code);
            }
            if (total > 0) ModelManager.setTotal(c, total);

            in = con.getInputStream();
            out = new FileOutputStream(part, append);
            byte[] buf = new byte[64 * 1024];
            long cur = done;
            long lastBytes = cur;
            long lastTime = SystemClock.elapsedRealtime();
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancelReq) return END_CANCELLED;
                if (pauseReq) return END_PAUSED;
                if (destroyed) return END_INTERRUPTED;
                out.write(buf, 0, n);
                cur += n;
                long now = SystemClock.elapsedRealtime();
                if (now - lastTime >= 1000) {
                    speed = (cur - lastBytes) * 1000 / (now - lastTime);
                    lastBytes = cur;
                    lastTime = now;
                    notifyProgress(cur, total);
                }
            }
            out.flush();
            if (total > 0 && cur < total) {
                throw new IOException("التحميل اتقطع قبل ما يكمل");
            }
            return 0;
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception e) {
            }
            try {
                if (in != null) in.close();
            } catch (Exception e) {
            }
            con.disconnect();
        }
    }

    private int unzip(File zip, File dest) throws IOException {
        dest.mkdirs();
        String root = dest.getCanonicalPath() + File.separator;
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
        try {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            int count = 0;
            while ((e = zin.getNextEntry()) != null) {
                if (cancelReq) return END_CANCELLED;
                if (destroyed) return END_INTERRUPTED;
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
                if (count % 5 == 0) {
                    detail = "فك الضغط: " + count + " ملف";
                    notifyUnzip(detail);
                }
            }
        } finally {
            zin.close();
        }
        return 0;
    }

    private static long parseLen(String s) {
        if (s == null) return -1;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- نهاية الشغل

    private void handleEnd(int r) {
        if (destroyed) return;
        releaseLocks();
        worker = null;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (r == END_INTERRUPTED) {
            stopSelf();
            return;
        }
        if (r == END_CANCELLED) {
            ModelManager.cleanup(this);
            stopForeground(true);
            nm.cancel(NOTIF_ID);
            stopSelf();
            return;
        }

        Notification n;
        if (r == END_DONE) {
            ModelManager.cleanup(this);
            n = buildDone(this);
        } else if (r == END_PAUSED) {
            ModelManager.setState(this, ModelManager.S_PAUSED);
            ModelManager.setUserPaused(this, true);
            n = buildPaused(this);
        } else {
            ModelManager.setState(this, ModelManager.S_ERROR);
            n = buildError(this, ModelManager.getError(this));
        }
        detachForeground();
        nm.notify(NOTIF_ID, n);
        stopSelf();
    }

    private void detachForeground() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(Service.STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
        }
    }

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voicenotes:modeldl");
            wakeLock.acquire(3 * 60 * 60 * 1000L);
        } catch (Exception e) {
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "voicenotes:modeldl");
            wifiLock.acquire();
        } catch (Exception e) {
        }
    }

    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception e) {
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception e) {
        }
        wakeLock = null;
        wifiLock = null;
    }

    // ---------------------------------------------------------------- الإشعارات

    private void notifyProgress(long done, long total) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildProgress(this, done, total, speed, null));
    }

    private void notifyMsg(String msg) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildProgress(this, ModelManager.partialBytes(this),
                ModelManager.getTotal(this), 0, msg));
    }

    private void notifyUnzip(String msg) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildUnzip(this, msg));
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "تحميل الموديل",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("تقدّم تحميل موديل التعرف على الصوت");
                nm.createNotificationChannel(ch);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder base(Context c) {
        ensureChannel(c);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CHANNEL_ID);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_LOW);
        }
        b.setSmallIcon(R.drawable.ic_stat_download);
        b.setShowWhen(false);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent open = new Intent(c, SetupActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        b.setContentIntent(PendingIntent.getActivity(c, 0, open, fl));
        return b;
    }

    @SuppressWarnings("deprecation")
    private static void addAction(Notification.Builder b, Context c, String title, String action, int req) {
        Intent i = new Intent(c, ModelDownloadService.class);
        i.setAction(action);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= 26) {
            pi = PendingIntent.getForegroundService(c, req, i, fl);
        } else {
            pi = PendingIntent.getService(c, req, i, fl);
        }
        b.addAction(0, title, pi);
    }

    static Notification buildProgress(Context c, long done, long total, long spd, String extra) {
        Notification.Builder b = base(c);
        b.setContentTitle("جاري تحميل الموديل");
        b.setOngoing(true);
        b.setOnlyAlertOnce(true);
        String text;
        String big;
        if (total > 0) {
            int pct = (int) (done * 100 / total);
            text = ModelManager.fmtSize(done) + " من " + ModelManager.fmtSize(total) + " • " + pct + "%";
            big = text + "\nالمتبقي: " + ModelManager.fmtSize(total - done);
            if (spd > 0) {
                big += " • " + ModelManager.fmtSize(spd) + "/ث";
                long eta = (total - done) / spd;
                big += "\nباقي حوالي " + ModelManager.fmtTime(eta);
            }
            b.setProgress(1000, (int) (done * 1000 / total), false);
        } else {
            text = ModelManager.fmtSize(done);
            big = text;
            b.setProgress(0, 0, true);
        }
        if (extra != null) {
            text = extra;
            big = big + "\n" + extra;
        }
        b.setContentText(text);
        b.setStyle(new Notification.BigTextStyle().bigText(big));
        addAction(b, c, "⏸ إيقاف مؤقت", ACTION_PAUSE, 1);
        addAction(b, c, "✖ إلغاء", ACTION_CANCEL, 2);
        return b.build();
    }

    static Notification buildUnzip(Context c, String msg) {
        Notification.Builder b = base(c);
        b.setContentTitle("جاري تجهيز الموديل");
        b.setContentText(msg);
        b.setOngoing(true);
        b.setOnlyAlertOnce(true);
        b.setProgress(0, 0, true);
        addAction(b, c, "✖ إلغاء", ACTION_CANCEL, 2);
        return b.build();
    }

    static Notification buildPaused(Context c) {
        long done = ModelManager.partialBytes(c);
        long total = ModelManager.getTotal(c);
        Notification.Builder b = base(c);
        b.setContentTitle("التحميل متوقف مؤقتاً");
        b.setOngoing(false);
        b.setOnlyAlertOnce(true);
        if (total > 0) {
            int pct = (int) (done * 100 / total);
            b.setContentText(ModelManager.fmtSize(done) + " من " + ModelManager.fmtSize(total)
                    + " • " + pct + "% • اضغط متابعة");
            b.setProgress(1000, (int) (done * 1000 / total), false);
        } else {
            b.setContentText(ModelManager.fmtSize(done) + " • اضغط متابعة");
        }
        addAction(b, c, "▶ متابعة", ACTION_RESUME, 3);
        addAction(b, c, "✖ إلغاء", ACTION_CANCEL, 2);
        return b.build();
    }

    static Notification buildError(Context c, String msg) {
        Notification.Builder b = base(c);
        b.setContentTitle("فشل تحميل الموديل");
        b.setContentText(msg == null || msg.length() == 0 ? "اضغط متابعة للمحاولة تاني" : msg);
        b.setStyle(new Notification.BigTextStyle().bigText(
                (msg == null ? "" : msg + "\n") + "اللي اتحمل محفوظ، اضغط متابعة."));
        b.setOngoing(false);
        addAction(b, c, "▶ متابعة", ACTION_RESUME, 3);
        addAction(b, c, "✖ إلغاء", ACTION_CANCEL, 2);
        return b.build();
    }

    static Notification buildDone(Context c) {
        Notification.Builder b = base(c);
        b.setContentTitle("تم تحميل الموديل ✅");
        b.setContentText("تقدر دلوقتي تسجّل نوتس من غير نت");
        b.setOngoing(false);
        b.setAutoCancel(true);
        return b.build();
    }
}
