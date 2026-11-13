package com.mohamed.voicenotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class NoteActivity extends BaseActivity {

    private NotesDb db;
    private long id;
    private EditText etTitle, etBody;
    private boolean deleted = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_note);
        db = new NotesDb(this);
        id = getIntent().getLongExtra("id", -1);
        Note n = db.get(id);
        if (n == null) {
            finish();
            return;
        }

        etTitle = (EditText) findViewById(R.id.etTitle);
        etBody = (EditText) findViewById(R.id.etBody);
        etTitle.setText(n.title);
        etBody.setText(n.body);

        findViewById(R.id.btnSaveNote).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                save();
                Toast.makeText(NoteActivity.this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnTranslateNote).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TranslateDialog.show(NoteActivity.this, etBody.getText().toString(),
                        new TranslateDialog.OnUse() {
                            public void use(String t, boolean replace) {
                                if (replace) {
                                    etBody.setText(t);
                                } else {
                                    if (etBody.getText().length() > 0) etBody.append("\n\n");
                                    etBody.append(t);
                                }
                            }
                        });
            }
        });
        findViewById(R.id.btnCopy).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("note", etBody.getText().toString()));
                Toast.makeText(NoteActivity.this, "تم النسخ", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, etTitle.getText().toString());
                i.putExtra(Intent.EXTRA_TEXT, etBody.getText().toString());
                startActivity(Intent.createChooser(i, "مشاركة النوت"));
            }
        });
        findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmDelete();
            }
        });

        Ui.rtl(this);
    }

    private void save() {
        if (etTitle == null) return;
        db.update(id, etTitle.getText().toString().trim(), etBody.getText().toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!deleted) save();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage("متأكد إنك عاوز تمسح النوت؟")
                .setPositiveButton("مسح", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        deleted = true;
                        db.delete(id);
                        finish();
                    }
                })
                .setNegativeButton("لأ", null)
                .show();
    }
}
