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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lib Dumper - pick .so inside app, dump selected only. Needs root. */
public class MainActivity extends Activity {

    private LinearLayout listBox;
    private LinearLayout libBox;
    private TextView logView;
    private TextView statusView;
    private EditText filterInput;
    private final List<AppRow> apps = new ArrayList<>();
    private final List<LibRow> libs = new ArrayList<>();
    private final Set<String> selectedLibs = new LinkedHashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String selectedPkg = null;
    private String cachedPid = null;
    private String cachedMaps = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.parseColor("#0B0F14"));

        TextView title = new TextView(this);
        title.setText("Lib Dumper");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setGravity(android.view.Gravity.CENTER);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("1) Select package  2) Scan libs  3) Check .so  4) Dump");
        statusView.setTextColor(Color.parseColor("#9AA0A6"));
        statusView.setTextSize(12);
        statusView.setPadding(0, 8, 0, 12);
        root.addView(statusView);

        filterInput = new EditText(this);
        filterInput.setHint("Filter package (freefire, dts...)");
        filterInput.setHintTextColor(Color.parseColor("#666666"));
        filterInput.setTextColor(Color.WHITE);
        filterInput.setBackgroundColor(Color.parseColor("#1A2332"));
        filterInput.setPadding(20, 14, 20, 14);
        filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { renderApps(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(filterInput);

        Button refresh = btn("Refresh apps");
        refresh.setOnClickListener(v -> loadApps());
        root.addView(refresh);

        TextView h1 = section("Packages");
        root.addView(h1);
        ScrollView appScroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        appScroll.addView(listBox);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        appScroll.setLayoutParams(alp);
        root.addView(appScroll);

        Button scan = btn("Scan libs of selected package");
        scan.setBackgroundColor(Color.parseColor("#1565C0"));
        scan.setOnClickListener(v -> {
            if (selectedPkg == null) { toast("Select a package first"); return; }
            scanLibs(selectedPkg);
        });
        root.addView(scan);

        TextView h2 = section("Libs (.so) - tap to select");
        root.addView(h2);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        Button selAll = btnSmall("All");
        selAll.setOnClickListener(v -> { for (LibRow r : libs) selectedLibs.add(r.path); renderLibs(); });
        Button selNone = btnSmall("None");
        selNone.setOnClickListener(v -> { selectedLibs.clear(); renderLibs(); });
        Button selIl2 = btnSmall("il2cpp/unity");
        selIl2.setOnClickListener(v -> {
            selectedLibs.clear();
            for (LibRow r : libs) {
                String n = r.name.toLowerCase(Locale.US);
                if (n.contains("il2cpp") || n.contains("unity") || n.contains("main")
                        || n.contains("anogs") || n.contains("tersafe"))
                    selectedLibs.add(r.path);
            }
            renderLibs();
        });
        quick.addView(selAll);
        quick.addView(selNone);
        quick.addView(selIl2);
        root.addView(quick);

        ScrollView libScroll = new ScrollView(this);
        libBox = new LinearLayout(this);
        libBox.setOrientation(LinearLayout.VERTICAL);
        libScroll.addView(libBox);
        LinearLayout.LayoutParams llp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        libScroll.setLayoutParams(llp2);
        root.addView(libScroll);

        Button dump = btn("DUMP SELECTED LIBS");
        dump.setBackgroundColor(Color.parseColor("#2E7D32"));
        dump.setOnClickListener(v -> {
            if (selectedPkg == null) { toast("Select package"); return; }
            if (selectedLibs.isEmpty()) { toast("Select at least one .so"); return; }
            startDump();
        });
        root.addView(dump);

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#B0BEC5"));
        logView.setTextSize(11);
        logView.setText("Log...\n");
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logView);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
        logScroll.setLayoutParams(logLp);
        root.addView(logScroll);

        setContentView(root);
        loadApps();
    }

    private TextView section(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.parseColor("#00E5FF"));
        tv.setTextSize(13);
        tv.setPadding(0, 10, 0, 4);
        return tv;
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
        renderApps();
        log("Loaded " + apps.size() + " apps");
    }

    private void renderApps() {
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
            row.setTextSize(12);
            row.setPadding(12, 8, 12, 8);
            row.setBackgroundColor(sel ? Color.parseColor("#1A2A3A") : Color.TRANSPARENT);
            row.setOnClickListener(v -> {
                selectedPkg = r.pkg;
                libs.clear();
                selectedLibs.clear();
                cachedPid = null;
                cachedMaps = null;
                renderApps();
                renderLibs();
                statusView.setText("Selected: " + r.pkg + "  -> Scan libs");
                log("Selected: " + r.pkg);
            });
            listBox.addView(row);
        }
    }

    private void scanLibs(String pkg) {
        statusView.setText("Scanning " + pkg + "...");
        toast("Scanning maps...");
        io.execute(() -> {
            try {
                if (!hasRoot()) {
                    ui.post(() -> { log("FAIL: need root"); statusView.setText("Need root"); });
                    return;
                }
                String pidStr = suOut("pidof " + pkg).trim();
                if (pidStr.isEmpty()) {
                    ui.post(() -> {
                        log("FAIL: process not running. Open app first.");
                        statusView.setText("Process not running");
                    });
                    return;
                }
                String pid = pidStr.split("\\s+")[0];
                String maps = suOut("cat /proc/" + pid + "/maps");
                LinkedHashMap<String, long[]> map = new LinkedHashMap<>();
                for (String line : maps.split("\n")) {
                    if (!line.contains(".so")) continue;
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 6) continue;
                    if (!parts[1].contains("r")) continue;
                    String path = parts[parts.length - 1];
                    if (!path.startsWith("/") || !path.endsWith(".so")) continue;
                    String[] se = parts[0].split("-");
                    long start = Long.parseLong(se[0], 16);
                    long end = Long.parseLong(se[1], 16);
                    if (!map.containsKey(path)) map.put(path, new long[]{start, end});
                    else {
                        long[] r = map.get(path);
                        if (end > r[1]) r[1] = end;
                    }
                }
                List<LibRow> found = new ArrayList<>();
                for (Map.Entry<String, long[]> e : map.entrySet()) {
                    LibRow lr = new LibRow();
                    lr.path = e.getKey();
                    lr.name = e.getKey().substring(e.getKey().lastIndexOf('/') + 1);
                    lr.start = e.getValue()[0];
                    lr.end = e.getValue()[1];
                    lr.size = e.getValue()[1] - e.getValue()[0];
                    found.add(lr);
                }
                Collections.sort(found, (a, b) -> a.name.compareToIgnoreCase(b.name));
                cachedPid = pid;
                cachedMaps = maps;
                ui.post(() -> {
                    libs.clear();
                    libs.addAll(found);
                    selectedLibs.clear();
                    for (LibRow r : libs) {
                        String n = r.name.toLowerCase(Locale.US);
                        if (n.contains("il2cpp") || n.contains("unity") || n.contains("main"))
                            selectedLibs.add(r.path);
                    }
                    renderLibs();
                    statusView.setText(found.size() + " libs  |  PID " + pid + "  |  tick to select");
                    log("Found " + found.size() + " .so  pid=" + pid);
                });
            } catch (Exception e) {
                ui.post(() -> log("ERROR: " + e.getMessage()));
            }
        });
    }

    private void renderLibs() {
        libBox.removeAllViews();
        if (libs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No libs yet. Select package + Scan.");
            empty.setTextColor(Color.parseColor("#666666"));
            empty.setPadding(8, 8, 8, 8);
            libBox.addView(empty);
            return;
        }
        for (LibRow r : libs) {
            CheckBox cb = new CheckBox(this);
            boolean on = selectedLibs.contains(r.path);
            cb.setChecked(on);
            String sizeStr = r.size > 1024 * 1024
                    ? String.format(Locale.US, "%.1f MB", r.size / (1024f * 1024f))
                    : String.format(Locale.US, "%.0f KB", r.size / 1024f);
            cb.setText(r.name + "  (" + sizeStr + ")");
            cb.setTextColor(Color.WHITE);
            cb.setTextSize(12);
            cb.setPadding(4, 6, 4, 6);
            final String path = r.path;
            cb.setOnClickListener(v -> {
                if (((CheckBox) v).isChecked()) selectedLibs.add(path);
                else selectedLibs.remove(path);
                statusView.setText(selectedLibs.size() + " selected / " + libs.size() + " libs");
            });
            libBox.addView(cb);
        }
    }

    private void startDump() {
        final String pkg = selectedPkg;
        final List<String> paths = new ArrayList<>(selectedLibs);
        log("Dump " + paths.size() + " libs from " + pkg);
        toast("Dumping " + paths.size() + " files...");
        statusView.setText("Dumping...");
        io.execute(() -> {
            try {
                String result = doDump(pkg, paths);
                ui.post(() -> {
                    log(result);
                    statusView.setText("Done -> /sdcard/LibDumps/");
                    toast("Done");
                });
            } catch (Exception e) {
                ui.post(() -> log("ERROR: " + e.getMessage()));
            }
        });
    }

    private String doDump(String pkg, List<String> paths) throws Exception {
        if (!hasRoot()) return "FAIL: no root";
        String pid = cachedPid;
        if (pid == null) {
            String pidStr = suOut("pidof " + pkg).trim();
            if (pidStr.isEmpty()) return "FAIL: process not running";
            pid = pidStr.split("\\s+")[0];
        }
        String maps = cachedMaps != null ? cachedMaps : suOut("cat /proc/" + pid + "/maps");
        LinkedHashMap<String, long[]> ranges = new LinkedHashMap<>();
        for (String line : maps.split("\n")) {
            if (!line.contains(".so")) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) continue;
            if (!parts[1].contains("r")) continue;
            String path = parts[parts.length - 1];
            if (!paths.contains(path)) continue;
            String[] se = parts[0].split("-");
            long start = Long.parseLong(se[0], 16);
            long end = Long.parseLong(se[1], 16);
            if (!ranges.containsKey(path)) ranges.put(path, new long[]{start, end});
            else {
                long[] r = ranges.get(path);
                if (end > r[1]) r[1] = end;
            }
        }

        File base = new File(Environment.getExternalStorageDirectory(), "LibDumps");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File outDir = new File(base, pkg + "/" + stamp);
        suOut("mkdir -p \"" + outDir.getAbsolutePath() + "\"");

        StringBuilder report = new StringBuilder();
        report.append("PID=").append(pid).append("\nOUT=").append(outDir.getAbsolutePath()).append("\n");
        int dumped = 0;
        for (String path : paths) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            File dest = new File(outDir, name);
            String cp = suOut("cp \"" + path + "\" \"" + dest.getAbsolutePath() + "\" 2>&1; echo EXIT:$?");
            if (cp.contains("EXIT:0") && destExists(dest.getAbsolutePath())) {
                report.append("OK cp  ").append(name).append(" (").append(fileSize(dest.getAbsolutePath())).append(")\n");
                dumped++;
                continue;
            }
            long[] r = ranges.get(path);
            if (r == null) {
                report.append("SKIP ").append(name).append(" (not in maps)\n");
                continue;
            }
            long size = r[1] - r[0];
            if (size <= 0 || size > 256L * 1024 * 1024) {
                report.append("SKIP ").append(name).append(" size\n");
                continue;
            }
            suOut("dd if=/proc/" + pid + "/mem bs=4096 skip=" + (r[0] / 4096)
                    + " count=" + ((size + 4095) / 4096)
                    + " of=\"" + dest.getAbsolutePath() + "\" 2>&1");
            suOut("dd if=\"" + dest.getAbsolutePath() + "\" of=\"" + dest.getAbsolutePath()
                    + ".trim\" bs=1 count=" + size + " 2>/dev/null; mv \""
                    + dest.getAbsolutePath() + ".trim\" \"" + dest.getAbsolutePath() + "\"");
            report.append("OK mem ").append(name).append(" (").append(fileSize(dest.getAbsolutePath()))
                    .append(") @0x").append(Long.toHexString(r[0])).append("\n");
            dumped++;
        }
        suOut("cp /proc/" + pid + "/maps \"" + outDir.getAbsolutePath() + "/maps.txt\"");
        report.append("TOTAL: ").append(dumped).append("\nFOLDER: ").append(outDir.getAbsolutePath()).append("\n");
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
        lp.topMargin = 6;
        b.setLayoutParams(lp);
        return b;
    }

    private Button btnSmall(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setBackgroundColor(Color.parseColor("#263238"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(2, 4, 2, 4);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    static class AppRow { String pkg; String label; }
    static class LibRow {
        String path;
        String name;
        long start, end, size;
    }
}
