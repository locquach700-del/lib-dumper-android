package com.libdumper.app;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lib Dumper - dump native .so from running process. Needs root. */
public class MainActivity extends Activity {

    private LinearLayout listBox;
    private TextView logView;
    private EditText filterInput;
    private EditText nameFilter;
    private final List<AppRow> apps = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String selectedPkg = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.parseColor("#0B0F14"));

        TextView title = new TextView(this);
        title.setText("Lib Dumper");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Dump .so from running process -> /sdcard/LibDumps/\nNeeds ROOT. Native libs only.");
        sub.setTextColor(Color.parseColor("#9AA0A6"));
        sub.setTextSize(12);
        sub.setPadding(0, 8, 0, 16);
        root.addView(sub);

        filterInput = new EditText(this);
        filterInput.setHint("Filter package (e.g. freefire, dts)");
        filterInput.setHintTextColor(Color.parseColor("#666666"));
        filterInput.setTextColor(Color.WHITE);
        filterInput.setBackgroundColor(Color.parseColor("#1A2332"));
        filterInput.setPadding(24, 16, 24, 16);
        filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { renderList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(filterInput);

        nameFilter = new EditText(this);
        nameFilter.setHint("Filter lib name (empty = all). e.g. il2cpp");
        nameFilter.setHintTextColor(Color.parseColor("#666666"));
        nameFilter.setTextColor(Color.WHITE);
        nameFilter.setBackgroundColor(Color.parseColor("#1A2332"));
        nameFilter.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = 8;
        nameFilter.setLayoutParams(nlp);
        root.addView(nameFilter);

        Button refresh = btn("Refresh app list");
        refresh.setOnClickListener(v -> loadApps());
        root.addView(refresh);

        ScrollView listScroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listBox);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = 12;
        listScroll.setLayoutParams(lp);
        root.addView(listScroll);

        Button dumpAll = btn("DUMP LIBS (selected package)");
        dumpAll.setBackgroundColor(Color.parseColor("#0D47A1"));
        dumpAll.setOnClickListener(v -> {
            if (selectedPkg == null) { toast("Select a package first"); return; }
            startDump(selectedPkg, false);
        });
        root.addView(dumpAll);

        Button dumpIl2 = btn("DUMP il2cpp / unity only");
        dumpIl2.setOnClickListener(v -> {
            if (selectedPkg == null) { toast("Select a package first"); return; }
            startDump(selectedPkg, true);
        });
        root.addView(dumpIl2);

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#B0BEC5"));
        logView.setTextSize(11);
        logView.setText("Log...\n");
        logView.setPadding(0, 12, 0, 0);
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logView);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        logScroll.setLayoutParams(llp);
        root.addView(logScroll);

        setContentView(root);
        loadApps();
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            AppRow r = new AppRow();
            r.pkg = ai.packageName;
            r.label = ai.loadLabel(pm).toString();
            apps.add(r);
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        renderList();
        log("Loaded " + apps.size() + " user apps");
    }

    private void renderList() {
        listBox.removeAllViews();
        String q = filterInput.getText() != null
                ? filterInput.getText().toString().trim().toLowerCase(Locale.US) : "";
        for (AppRow r : apps) {
            if (q.length() > 0
                    && !r.pkg.toLowerCase(Locale.US).contains(q)
                    && !r.label.toLowerCase(Locale.US).contains(q)) continue;
            TextView row = new TextView(this);
            boolean sel = r.pkg.equals(selectedPkg);
            row.setText((sel ? "> " : "  ") + r.label + "\n    " + r.pkg);
            row.setTextColor(sel ? Color.parseColor("#00E5FF") : Color.WHITE);
            row.setTextSize(13);
            row.setPadding(16, 12, 16, 12);
            row.setBackgroundColor(sel ? Color.parseColor("#1A2A3A") : Color.TRANSPARENT);
            row.setOnClickListener(v -> {
                selectedPkg = r.pkg;
                renderList();
                log("Selected: " + r.pkg);
            });
            listBox.addView(row);
        }
    }

    private void startDump(String pkg, boolean onlyGameLibs) {
        String libQ = nameFilter.getText() != null
                ? nameFilter.getText().toString().trim().toLowerCase(Locale.US) : "";
        log("Dump start: " + pkg + (onlyGameLibs ? " [il2cpp/unity]" : ""));
        toast("Dumping... check log");
        io.execute(() -> {
            try {
                String result = doDump(pkg, onlyGameLibs, libQ);
                ui.post(() -> { log(result); toast("Done - see /sdcard/LibDumps/"); });
            } catch (Exception e) {
                ui.post(() -> log("ERROR: " + e.getMessage()));
            }
        });
    }

    private String doDump(String pkg, boolean onlyGameLibs, String libNameFilter) throws Exception {
        if (!hasRoot()) return "FAIL: no root (su). Need root to read /proc/<pid>/mem.";
        String pidStr = suOut("pidof " + pkg).trim();
        if (pidStr.isEmpty()) return "FAIL: process not running. Open the game first.";
        String pid = pidStr.split("\\s+")[0];
        File base = new File(Environment.getExternalStorageDirectory(), "LibDumps");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File outDir = new File(base, pkg + "/" + stamp);
        suOut("mkdir -p \"" + outDir.getAbsolutePath() + "\"");
        String maps = suOut("cat /proc/" + pid + "/maps");
        StringBuilder report = new StringBuilder();
        report.append("PID=").append(pid).append("\nOUT=").append(outDir.getAbsolutePath()).append("\n");
        int dumped = 0;
        LinkedHashMap<String, long[]> firstRange = new LinkedHashMap<>();
        for (String line : maps.split("\n")) {
            if (!line.contains(".so")) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) continue;
            if (!parts[1].contains("r")) continue;
            String path = parts[parts.length - 1];
            if (!path.startsWith("/") || !path.endsWith(".so")) continue;
            String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.US);
            if (onlyGameLibs) {
                if (!(name.contains("il2cpp") || name.contains("unity") || name.contains("main")
                        || name.contains("anogs") || name.contains("tersafe") || name.contains("gcloud")))
                    continue;
            }
            if (libNameFilter.length() > 0 && !name.contains(libNameFilter)) continue;
            String[] se = parts[0].split("-");
            long start = Long.parseLong(se[0], 16);
            long end = Long.parseLong(se[1], 16);
            if (!firstRange.containsKey(path)) firstRange.put(path, new long[]{start, end});
            else {
                long[] r = firstRange.get(path);
                if (end > r[1]) r[1] = end;
            }
        }
        for (String path : firstRange.keySet()) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            File dest = new File(outDir, name);
            String cp = suOut("cp \"" + path + "\" \"" + dest.getAbsolutePath() + "\" 2>&1; echo EXIT:$?");
            if (cp.contains("EXIT:0") && destExists(dest.getAbsolutePath())) {
                report.append("OK cp  ").append(name).append(" (").append(fileSize(dest.getAbsolutePath())).append(" bytes)\n");
                dumped++;
                continue;
            }
            long[] r = firstRange.get(path);
            long size = r[1] - r[0];
            if (size <= 0 || size > 256L * 1024 * 1024) {
                report.append("SKIP ").append(name).append("\n");
                continue;
            }
            suOut("dd if=/proc/" + pid + "/mem bs=4096 skip=" + (r[0] / 4096)
                    + " count=" + ((size + 4095) / 4096)
                    + " of=\"" + dest.getAbsolutePath() + "\" 2>&1");
            suOut("dd if=\"" + dest.getAbsolutePath() + "\" of=\"" + dest.getAbsolutePath()
                    + ".trim\" bs=1 count=" + size + " 2>/dev/null; mv \""
                    + dest.getAbsolutePath() + ".trim\" \"" + dest.getAbsolutePath() + "\"");
            report.append("OK mem ").append(name).append(" (").append(fileSize(dest.getAbsolutePath()))
                    .append(" bytes) @0x").append(Long.toHexString(r[0])).append("\n");
            dumped++;
        }
        suOut("cp /proc/" + pid + "/maps \"" + outDir.getAbsolutePath() + "/maps.txt\"");
        report.append("maps.txt saved\nTOTAL libs: ").append(dumped).append("\nFOLDER: ")
                .append(outDir.getAbsolutePath()).append("\n");
        return report.toString();
    }

    private boolean hasRoot() {
        try { return suOut("id").contains("uid=0"); } catch (Exception e) { return false; }
    }

    private String suOut(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes(cmd + "\n");
        os.writeBytes("exit\n");
        os.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        while ((line = e.readLine()) != null) sb.append(line).append('\n');
        p.waitFor();
        return sb.toString();
    }

    private boolean destExists(String path) throws Exception {
        return suOut(" [ -f \"" + path + "\" ] && echo YES").contains("YES");
    }

    private long fileSize(String path) throws Exception {
        String o = suOut("stat -c %s \"" + path + "\" 2>/dev/null || wc -c < \"" + path + "\"");
        try { return Long.parseLong(o.trim().split("\\s+")[0]); } catch (Exception e) { return 0; }
    }

    private void log(String s) { logView.append(s + "\n"); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#1A2332"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8;
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    static class AppRow { String pkg; String label; }
}
