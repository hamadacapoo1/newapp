package com.mohamed.voicenotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private NotesDb db;
    private EditText etSearch;
    private TextView tvModel;
    private Button btnTheme;
    private ListView list;
    private ArrayAdapter<String> adapter;
    private final List<Note> notes = new ArrayList<Note>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        db = new NotesDb(this);

        tvModel = (TextView) findViewById(R.id.tvModel);
        etSearch = (EditText) findViewById(R.id.etSearch);
        btnTheme = (Button) findViewById(R.id.btnTheme);
        btnTheme.setText(Prefs.isDark(this) ? "☀ نهاري" : "🌙 ليلي");
        btnTheme.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setDark(MainActivity.this, !Prefs.isDark(MainActivity.this));
                recreate();
            }
        });
        list = (ListView) findViewById(R.id.lvNotes);

        tvModel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SetupActivity.class));
            }
        });
        findViewById(R.id.btnNew).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, RecordActivity.class));
            }
        });
        findViewById(R.id.btnSetup).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SetupActivity.class));
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b2, int c) {
            }

            public void afterTextChanged(Editable s) {
                refresh();
            }
        });

        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int pos, long rowId) {
                Intent i = new Intent(MainActivity.this, NoteActivity.class);
                i.putExtra("id", notes.get(pos).id);
                startActivity(i);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View v, int pos, long rowId) {
                confirmDelete(notes.get(pos));
                return true;
            }
        });

        Ui.rtl(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean dark = Prefs.isDark(this);
        int green = dark ? Color.rgb(102, 187, 106) : Color.rgb(0, 120, 0);
        int orange = dark ? Color.rgb(255, 167, 38) : Color.rgb(200, 90, 0);
        String st = ModelManager.getState(this);
        if (ModelManager.isReady(this)) {
            tvModel.setText("✅ الموديل جاهز (بيشتغل أوفلاين)");
            tvModel.setTextColor(green);
        } else if (ModelManager.S_DOWNLOADING.equals(st) || ModelManager.S_UNZIPPING.equals(st)) {
            if (ModelDownloadService.isRunning()) {
                tvModel.setText("⏬ الموديل بيتحمل في الخلفية — اضغط لمتابعة التقدّم");
            } else {
                tvModel.setText("⏸ تحميل الموديل اتوقف — اضغط هنا وكمّل");
            }
            tvModel.setTextColor(orange);
        } else if (ModelManager.S_PAUSED.equals(st) || ModelManager.partialBytes(this) > 0) {
            tvModel.setText("⏸ تحميل الموديل متوقف — اضغط هنا وكمّل");
            tvModel.setTextColor(orange);
        } else {
            tvModel.setText("⚠ لازم تحمّل الموديل العربي أول مرة — اضغط هنا");
            tvModel.setTextColor(orange);
        }
        refresh();
    }

    private void refresh() {
        notes.clear();
        notes.addAll(db.search(etSearch.getText().toString()));
        adapter.clear();
        for (Note n : notes) {
            adapter.add(n.title + "\n" + fmt.format(new Date(n.created)));
        }
        adapter.notifyDataSetChanged();
    }

    private void confirmDelete(final Note n) {
        new AlertDialog.Builder(this)
                .setMessage("تمسح النوت دي؟")
                .setPositiveButton("مسح", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        db.delete(n.id);
                        refresh();
                    }
                })
                .setNegativeButton("لأ", null)
                .show();
    }
}
