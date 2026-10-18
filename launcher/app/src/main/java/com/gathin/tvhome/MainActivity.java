package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;

/** Lightweight TV home. No services, network queries or animated effects. */
public class MainActivity extends Activity implements AppAdapter.TileListener {
    static int COLUMNS = 4;
    private AppAdapter adapter;
    private RecyclerView grid;
    private ViewGroup root;
    private float density;
    private String liveTvPkg;
    private static String lastFocusedKey;
    private static int lastFocused;
    private boolean dirty = true;
    private boolean resumed;
    private final Runnable refresh = () -> {
        if (!resumed || !dirty) return;
        dirty = false;
        String key = lastFocusedKey;
        int slot = lastFocused;
        boolean restore = grid.hasFocus() || adapter.getItemCount() == 0;
        adapter.reload();
        if (restore) grid.post(() -> {
            int pos = adapter.positionOf(key);
            focusTile(pos >= 0 ? pos : slot);
        });
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            dirty = true;
            grid.removeCallbacks(refresh);
            if (resumed) grid.postDelayed(refresh, 250);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();
        grid = findViewById(R.id.grid);
        GridLayoutManager layout = new GridLayoutManager(this, COLUMNS);
        layout.setItemPrefetchEnabled(false);
        grid.setLayoutManager(layout);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.setItemViewCacheSize(0);
        grid.getRecycledViewPool().setMaxRecycledViews(0, COLUMNS);
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);
        findViewById(R.id.btnInputs).setOnClickListener(v -> showInputs());
        findViewById(R.id.btnLiveTv).setOnClickListener(v -> openLiveTv());
        findViewById(R.id.btnSettings).setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packages, filter);
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        refresh.run();
    }
    @Override protected void onPause() {
        resumed = false;
        grid.removeCallbacks(refresh);
        super.onPause();
    }
    @Override protected void onDestroy() {
        unregisterReceiver(packages);
        grid.removeCallbacks(refresh);
        grid.setAdapter(null);
        adapter.close();
        super.onDestroy();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (adapter != null) adapter.clearIcons();
    }
    @Override public void onBackPressed() { }
    @Override public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
    }
    @Override public boolean onTileVerticalKey(int position, int key) {
        int count = adapter.getItemCount();
        if (key == KeyEvent.KEYCODE_DPAD_UP && position < COLUMNS) {
            findViewById(R.id.btnInputs).requestFocus();
            return true;
        }
        int target = key == KeyEvent.KEYCODE_DPAD_UP ? position - COLUMNS : position + COLUMNS;
        if (target >= count && position / COLUMNS == (count - 1) / COLUMNS) return true;
        focusTile(Math.min(target, count - 1));
        return true;
    }
    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        grid.scrollToPosition(pos);
        grid.post(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(pos);
            if (holder != null) holder.itemView.findViewById(R.id.card).requestFocus();
        });
    }
    private boolean tryOpen(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) { return false; }
    }
    private void open(Intent intent) {
        if (!tryOpen(intent)) Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }
    private void openLiveTv() {
        if (!liveTvPkg.isEmpty()) {
            Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
            if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(liveTvPkg);
            if (intent != null && tryOpen(intent)) return;
        }
        if (tryOpen(new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI))) return;
        open(new Intent("android.intent.action.TV_INPUT"));
    }
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        if (rows.getChildCount() > 0) addSeparator(rows);
        addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, this::openLiveTv);
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
