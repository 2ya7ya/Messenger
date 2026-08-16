package com.facebook.messengerclone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.util.Base64;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageLoader {
    private final ApiClient api;
    private final File dir;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    ImageLoader(Context context, ApiClient api) {
        this.api = api;
        dir = new File(context.getCacheDir(), "messenger_images");
        if (!dir.exists()) dir.mkdirs();
    }

    void load(String url, ImageView view) {
        view.setTag(url);
        if (url == null || url.isEmpty()) { view.setImageDrawable(null); return; }
        Bitmap m = memory.get(url);
        if (m != null) { view.setImageBitmap(m); return; }
        executor.execute(() -> {
            try {
                File f = new File(dir, hash(url));
                byte[] bytes;
                if (url.startsWith("data:image/")) {
                    int comma = url.indexOf(',');
                    if (comma < 0) return;
                    bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
                } else if (f.exists()) bytes = java.nio.file.Files.readAllBytes(f.toPath());
                else {
                    bytes = api.getBytesSync(url);
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
                }
                Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (b != null) {
                    memory.put(url, b);
                    view.post(() -> { if (url.equals(view.getTag())) view.setImageBitmap(b); });
                }
            } catch (Exception ignored) {}
        });
    }

    private static String hash(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format("%02x", x)); return b.toString();
    }
}
