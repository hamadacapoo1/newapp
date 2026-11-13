package com.mohamed.voicenotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class SetupActivity extends BaseActivity {

    private EditText etUrl;
    private TextView tvStatus, tvState;
    private ProgressBar bar;
    private Button btnDownload, btnPause, btnCancel, btnDelete;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private long lastBytes = -1;
    private long lastTime = 0;
    private double speed = 0;

    private final Runnable ticker = new Runnable() {
        public void run() {
            refresh();
            ui.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_setup);

        tvState = (TextView) findViewById(R.id.tvState);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        etUrl = (EditText) findViewById(R.id.etUrl);
        bar = (ProgressBar) findViewById(R.id.pbDownload);
        btnDownload = (Button) findViewById(R.id.btnDownload);
        btnPause = (Button) findViewById(R.id.btnPause);
        btnCancel = (Button) findViewById(R.id.btnCancel);
        btnDelete = (Button) findViewById(R.id.btnDelete);

        bar.setMax(1000);
        etUrl.setText(ModelManager.getUrl(this));
        ((TextView) findViewById(R.id.tvManual)).setText(
                "لو التحميل فشل: نزّل ملف الموديل بنفسك وفك الضغط، وانسخ محتوى المجلد "
                + "(اللي جواه am و conf و graph) جوه المسار ده:\n\n"
                + ModelManager.baseDir(this).getAbsolutePath());

        btnDownload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDownload();
            }
        });
        btnPause.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                btnPause.setEnabled(false);
                ModelDownloadService.send(SetupActivity.this, ModelDownloadService.ACTION_PAUSE);
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmCancel();
            }
        });
        btnDelete.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmDelete();
            }
        });

        Ui.rtl(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastBytes = -1;
        speed = 0;
        ui.removeCallbacks(ticker);
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
    }

    // ---------------------------------------------------------------- عرض الحالة

    private void refresh() {
        Context c = this;
        boolean svc = ModelDownloadService.isRunning();
        String st = ModelManager.getState(c);

        // الموديل موجود بالفعل: مفيش داعي للتحميل
        if (ModelManager.isReady(c)) {
            if (!svc && !ModelManager.S_IDLE.equals(st)) ModelManager.cleanup(c);
            if (!svc) {
                tvState.setText("✅ الموديل جاهز (موجود على جهازك)");
                tvStatus.setText("مش محتاج تحمّله تاني.");
                bar.setIndeterminate(false);
                bar.setProgress(1000);
                etUrl.setEnabled(false);
                btnDownload.setText("⬇ تحميل الموديل");
                btnDownload.setEnabled(false);
                btnPause.setEnabled(false);
                btnCancel.setEnabled(false);
                btnDelete.setEnabled(true);
                lastBytes = -1;
                return;
            }
        }

        long done = ModelManager.partialBytes(c);
        long total = ModelManager.getTotal(c);

        String eff = st;
        if ((ModelManager.S_DOWNLOADING.equals(st) || ModelManager.S_UNZIPPING.equals(st)) && !svc) {
            eff = ModelManager.S_PAUSED;
        }
        if (ModelManager.S_IDLE.equals(eff) && done > 0) eff = ModelManager.S_PAUSED;

        boolean downloading = ModelManager.S_DOWNLOADING.equals(eff);
        boolean unzipping = ModelManager.S_UNZIPPING.equals(eff);
        boolean paused = ModelManager.S_PAUSED.equals(eff);
        boolean error = ModelManager.S_ERROR.equals(eff);
        boolean idle = ModelManager.S_IDLE.equals(eff);

        // السرعة
        if (downloading) {
            long now = SystemClock.elapsedRealtime();
            if (lastBytes < 0) {
                lastBytes = done;
                lastTime = now;
            } else if (now - lastTime >= 600) {
                double inst = (done - lastBytes) * 1000.0 / (now - lastTime);
                speed = speed <= 0 ? inst : (speed * 0.7 + inst * 0.3);
                lastBytes = done;
                lastTime = now;
            }
        } else {
            lastBytes = -1;
            speed = 0;
        }

        // العنوان
        if (idle) tvState.setText("⚠ الموديل لسه مش متحمل");
        else if (downloading) tvState.setText("⏬ جاري التحميل (شغال في الخلفية)");
        else if (unzipping) tvState.setText("📦 جاري فك الضغط وتجهيز الموديل");
        else if (paused) tvState.setText("⏸ التحميل متوقف مؤقتاً");
        else tvState.setText("❌ فشل التحميل");

        // التفاصيل
        StringBuilder sb = new StringBuilder();
        if (unzipping) {
            sb.append(ModelDownloadService.detail);
        } else if (!idle) {
            if (total > 0) {
                double pct = done * 100.0 / total;
                sb.append("اتحمل: ").append(ModelManager.fmtSize(done))
                        .append(" من ").append(ModelManager.fmtSize(total))
                        .append("  (").append(String.format(Locale.US, "%.1f", pct)).append("%)")
                        .append("\nالمتبقي: ").append(ModelManager.fmtSize(total - done));
            } else {
                sb.append("اتحمل: ").append(ModelManager.fmtSize(done));
            }
            if (downloading && speed > 1024) {
                sb.append("\nالسرعة: ").append(ModelManager.fmtSize((long) speed)).append("/ث");
                if (total > 0) {
                    sb.append("  •  باقي حوالي ")
                            .append(ModelManager.fmtTime((long) ((total - done) / speed)));
                }
            }
            if (error) {
                String err = ModelManager.getError(c);
                if (err.length() > 0) sb.append("\n").append(err);
            }
        }
        tvStatus.setText(sb.toString());

        // الشريط
        if (unzipping || (downloading && total <= 0)) {
            bar.setIndeterminate(true);
        } else {
            bar.setIndeterminate(false);
            bar.setProgress(total > 0 ? (int) (done * 1000 / total) : 0);
        }

        // الأزرار
        boolean hasPartial = done > 0;
        btnDownload.setText((paused || (error && hasPartial)) ? "▶ متابعة التحميل" : "⬇ تحميل الموديل");
        btnDownload.setEnabled(idle || paused || error);
        btnPause.setEnabled(downloading);
        btnCancel.setEnabled(downloading || unzipping || paused || (error && hasPartial));
        btnDelete.setEnabled(false);
        etUrl.setEnabled(idle && !hasPartial);
        if (!idle || hasPartial) etUrl.setText(ModelManager.getUrl(c));
    }

    // ---------------------------------------------------------------- الأوامر

    private void startDownload() {
        if (ModelManager.isReady(this)) {
            Toast.makeText(this, "الموديل موجود بالفعل على جهازك", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
        if (ModelManager.partialBytes(this) > 0) {
            // متابعة من حيث وقف
            ModelManager.setUserPaused(this, false);
            ModelManager.setState(this, ModelManager.S_DOWNLOADING);
        } else {
            String url = etUrl.getText().toString().trim();
            if (url.length() == 0) {
                Toast.makeText(this, "اكتب رابط الموديل", Toast.LENGTH_SHORT).show();
                return;
            }
            ModelManager.beginFresh(this, url);
        }
        ModelDownloadService.send(this, ModelDownloadService.ACTION_START);
        refresh();
    }

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setMessage("تلغي التحميل وتمسح اللي اتحمل لحد دلوقتي؟")
                .setPositiveButton("إلغاء التحميل", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (ModelDownloadService.isRunning()) {
                            ModelDownloadService.send(SetupActivity.this,
                                    ModelDownloadService.ACTION_CANCEL);
                        } else {
                            ModelManager.cleanup(SetupActivity.this);
                            NotificationManager nm = (NotificationManager)
                                    getSystemService(Context.NOTIFICATION_SERVICE);
                            nm.cancel(ModelDownloadService.NOTIF_ID);
                        }
                        refresh();
                    }
                })
                .setNegativeButton("رجوع", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage("متأكد إنك عاوز تمسح الموديل؟ هتحتاج تحمّله تاني.")
                .setPositiveButton("مسح", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        ModelManager.delete(SetupActivity.this);
                        tvStatus.setText("");
                        refresh();
                        Toast.makeText(SetupActivity.this, "تم الحذف", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("لأ", null)
                .show();
    }
}
