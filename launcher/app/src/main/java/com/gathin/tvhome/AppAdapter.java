package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.concurrent.*;

/** Entries contain metadata only; visible holders request small icons on one worker. */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
    interface TileListener {
        void onTileFocused(int position, View card);
        boolean onTileVerticalKey(int position, int keyCode);
    }
    private static final class Entry {
        final String label;
        final ComponentName component;
        Entry(String label, ComponentName component) { this.label = label; this.component = component; }
        String key() { return component.flattenToString(); }
    }
    static final float FOCUS_SCALE = 1.10f;
    private final Context context;
    private final TileListener listener;
    private final List<Entry> items = new ArrayList<>();
    private final List<String> favourites;
    private final Set<String> hidden;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private volatile boolean closed;
    private int generation;
    private final int iconSize;

    AppAdapter(Context context, TileListener listener, int columns) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        favourites = Arrays.asList(context.getResources().getStringArray(R.array.dock_apps));
        hidden = new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.hidden_apps)));
        iconSize = Math.min(96, Math.max(1, (int) (48 * context.getResources().getDisplayMetrics().density)));
        worker.allowCoreThreadTimeOut(true);
    }
    void reload() {
        generation++;
        clearIcons();
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        items.clear();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            ComponentName component = new ComponentName(pkg, info.activityInfo.name);
            if (pkg.equals(context.getPackageName()) || hidden.contains(pkg) || !seen.add(component.flattenToString())) continue;
            items.add(new Entry(String.valueOf(info.loadLabel(pm)), component));
        }
        Collections.sort(items, (a, b) -> {
            int x = favourites.indexOf(a.component.getPackageName());
            int y = favourites.indexOf(b.component.getPackageName());
            if (x != y && (x >= 0 || y >= 0)) return Integer.compare(x < 0 ? Integer.MAX_VALUE : x, y < 0 ? Integer.MAX_VALUE : y);
            return a.label.compareToIgnoreCase(b.label);
        });
        notifyDataSetChanged();
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        cancel(holder);
        Entry entry = items.get(position);
        holder.key = entry.key();
        holder.card.animate().cancel();
        holder.name.animate().cancel();
        boolean focused = holder.card.isFocused();
        holder.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        holder.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        holder.name.setVisibility(position < MainActivity.COLUMNS ? View.INVISIBLE : View.VISIBLE);
        holder.name.setAlpha(focused ? 1f : 0f);
        holder.name.setTranslationY((focused ? 4 : -6) * context.getResources().getDisplayMetrics().density);
        holder.name.setText(entry.label);
        holder.label.setText(entry.label);
        holder.banner.setImageDrawable(null);
        holder.banner.setVisibility(View.GONE);
        holder.icon.setVisibility(View.VISIBLE);
        holder.label.setVisibility(View.VISIBLE);
        holder.card.setContentDescription(entry.label);
        holder.icon.setImageResource(R.mipmap.ic_launcher);
        Bitmap cached = icons.get(holder.key);
        if (cached != null) showArtwork(holder, cached);
        else {
            final int requestGeneration = generation;
            final String key = holder.key;
            holder.task = worker.submit(() -> {
                Bitmap bitmap;
                try {
                    PackageManager pm = context.getPackageManager();
                    ActivityInfo info = pm.getActivityInfo(entry.component, 0);
                    Drawable drawable = info.loadBanner(pm);
                    if (drawable == null) drawable = info.applicationInfo.loadBanner(pm);
                    boolean banner = drawable != null;
                    if (drawable == null) drawable = pm.getActivityIcon(entry.component);
                    if (Thread.currentThread().isInterrupted()) return;
                    int width = banner ? 96 : iconSize;
                    int height = banner ? 54 : iconSize;
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    drawable.setBounds(0, 0, width, height);
                    drawable.draw(new Canvas(bitmap));
                } catch (PackageManager.NameNotFoundException | RuntimeException ex) { return; }
                if (Thread.currentThread().isInterrupted() || closed) return;
                main.post(() -> {
                    if (closed || generation != requestGeneration) return;
                    icons.put(key, bitmap);
                    if (key.equals(holder.key)) {
                        showArtwork(holder, bitmap);
                        int pos = holder.getBindingAdapterPosition();
                        if (holder.card.isFocused() && pos != RecyclerView.NO_POSITION)
                            listener.onTileFocused(pos, holder.card);
                    }
                });
            });
        }
        holder.card.setOnClickListener(v -> {
            try {
                context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(entry.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            } catch (ActivityNotFoundException | SecurityException ex) {
                Toast.makeText(context, context.getString(R.string.cant_open, entry.label), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void showArtwork(Holder holder, Bitmap bitmap) {
        boolean banner = bitmap.getWidth() > bitmap.getHeight();
        holder.banner.setImageBitmap(banner ? bitmap : null);
        holder.banner.setVisibility(banner ? View.VISIBLE : View.GONE);
        holder.icon.setImageBitmap(banner ? null : bitmap);
        holder.icon.setVisibility(banner ? View.GONE : View.VISIBLE);
        holder.label.setVisibility(banner ? View.GONE : View.VISIBLE);
    }
    String labelAt(int position) { return position >= 0 && position < items.size() ? items.get(position).label : ""; }
    int ambientColorAt(int position) {
        Bitmap bitmap = icons.get(keyAt(position) == null ? "" : keyAt(position));
        if (bitmap == null) return 0xFF444444;
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float score = -1f;
        for (int y = 0; y < bitmap.getHeight(); y += 8) {
            for (int x = 0; x < bitmap.getWidth(); x += 8) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) < 128) continue;
                Color.colorToHSV(pixel, hsv);
                float candidate = hsv[1] * hsv[2];
                if (candidate > score) { score = candidate; best = pixel; }
            }
        }
        return best;
    }
    private void cancel(Holder holder) {
        holder.key = null;
        if (holder.task != null) { holder.task.cancel(true); holder.task = null; worker.purge(); }
    }
    @Override public void onViewRecycled(@NonNull Holder holder) {
        cancel(holder);
        holder.card.animate().cancel();
        holder.name.animate().cancel();
        holder.banner.setImageDrawable(null);
        holder.icon.setImageDrawable(null);
        holder.card.setOnClickListener(null);
        super.onViewRecycled(holder);
    }
    void clearIcons() { icons.evictAll(); }
    void close() { closed = true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); clearIcons(); }
    @Override public int getItemCount() { return items.size(); }
    String keyAt(int position) { return position >= 0 && position < items.size() ? items.get(position).key() : null; }
    int positionOf(String key) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) return i;
        return -1;
    }
    final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView icon;
        final ImageView banner;
        final TextView label;
        final TextView name;
        String key;
        Future<?> task;
        Holder(View view) {
            super(view);
            card = view.findViewById(R.id.card);
            icon = view.findViewById(R.id.icon);
            name = view.findViewById(R.id.name);
            banner = view.findViewById(R.id.banner);
            label = view.findViewById(R.id.label);
            float density = view.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 16 * density);
                }
            });
            card.setClipToOutline(true);
            card.setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? FOCUS_SCALE : 1f).scaleY(focused ? FOCUS_SCALE : 1f)
                        .setDuration(focused ? 260 : 180)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(focused ? 1.6f : 0f)).start();
                name.animate().alpha(focused ? 1f : 0f).translationY((focused ? 4 : -6) * density)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && pos != RecyclerView.NO_POSITION) listener.onTileFocused(pos, v);
            });
            card.setOnKeyListener((v, code, event) -> {
                int pos = getBindingAdapterPosition();
                if (event.getAction() != KeyEvent.ACTION_DOWN || pos == RecyclerView.NO_POSITION) return false;
                if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)
                    return listener.onTileVerticalKey(pos, code);
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && pos % MainActivity.COLUMNS == 0) return true;
                return code == KeyEvent.KEYCODE_DPAD_RIGHT && (pos % MainActivity.COLUMNS == MainActivity.COLUMNS - 1 || pos == items.size() - 1);
            });
        }
    }
}
