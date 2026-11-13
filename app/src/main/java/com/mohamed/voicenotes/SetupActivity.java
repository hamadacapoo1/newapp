package com.mohamed.voicenotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {

    private EditText etUrl;
    private TextView tvStatus, tvState;
    private ProgressBar bar;
    private Button btnDownload, btnDelete;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_setup);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tvState = (TextView) findViewById(R.id.tvState);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        etUrl = (EditText) findViewById(R.id.etUrl);
        bar = (ProgressBar) findViewById(R.id.pbDownload);
        btnDownload = (Button) findViewById(R.id.btnDownload);
        btnDelete = (Button) findViewById(R.id.btnDelete);

        etUrl.setText(ModelManager.DEFAULT_URL);
        ((TextView) findViewById(R.id.tvManual)).setText(
                "لو التحميل فشل: نزّل ملف الموديل بنفسك وفك الضغط، وانسخ محتوى المجلد "
                + "(اللي جواه am و conf و graph) جوه المسار ده:\n\n"
                + ModelManager.baseDir(this).getAbsolutePath());

        btnDownload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDownload();
            }
        });
        btnDelete.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmDelete();
            }
        });

        Ui.rtl(this);
        refreshState();
    }

    private void refreshState() {
        boolean ready = ModelManager.isReady(this);
        tvState.setText(ready ? "✅ الموديل جاهز" : "⚠ الموديل لسه مش متحمل");
        btnDelete.setEnabled(ready && !busy);
        btnDownload.setEnabled(!busy);
    }

    private void startDownload() {
        if (busy) return;
        busy = true;
        refreshState();
        final String url = etUrl.getText().toString().trim();

        new Thread(new Runnable() {
            public void run() {
                try {
                    ModelManager.download(SetupActivity.this, url, new ModelManager.Progress() {
                        public void onProgress(final String msg, final int pct) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    tvStatus.setText(msg);
                                    if (pct >= 0) {
                                        bar.setIndeterminate(false);
                                        bar.setProgress(pct);
                                    } else {
                                        bar.setIndeterminate(true);
                                    }
                                }
                            });
                        }
                    });
                    runOnUiThread(new Runnable() {
                        public void run() {
                            busy = false;
                            bar.setIndeterminate(false);
                            bar.setProgress(100);
                            tvStatus.setText("تم ✅ تقدر ترجع وتسجّل.");
                            refreshState();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            busy = false;
                            bar.setIndeterminate(false);
                            bar.setProgress(0);
                            tvStatus.setText("فشل: " + e.getMessage());
                            refreshState();
                        }
                    });
                }
            }
        }).start();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage("متأكد إنك عاوز تمسح الموديل؟ هتحتاج تحمّله تاني.")
                .setPositiveButton("مسح", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        ModelManager.delete(SetupActivity.this);
                        tvStatus.setText("");
                        refreshState();
                        Toast.makeText(SetupActivity.this, "تم الحذف", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("لأ", null)
                .show();
    }
}
