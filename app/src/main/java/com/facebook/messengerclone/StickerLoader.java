package com.facebook.messengerclone;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class StickerLoader {
    private final ApiClient api;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final LruCache<String, byte[]> memory = new LruCache<String, byte[]>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, byte[] value) { return value == null ? 0 : value.length; }
    };

    StickerLoader(Context context, ApiClient api) { this.api = api; }

    byte[] getCachedOrFetch(String url) throws Exception {
        byte[] cached = memory.get(url);
        if (cached != null) return cached;
        byte[] bytes = api.getBytesSync(url);
        if (bytes != null && bytes.length > 0) memory.put(url, bytes);
        return bytes;
    }

    void load(String url, ImageView view) {
        view.setTag(url);
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (url == null || url.isEmpty()) { view.setImageDrawable(null); return; }
        byte[] cached = memory.get(url);
        if (cached != null) { setBytes(url, cached, view); return; }
        executor.execute(() -> {
            try {
                byte[] bytes = getCachedOrFetch(url);
                view.post(() -> setBytes(url, bytes, view));
            } catch (Exception ignored) {}
        });
    }

    private void setBytes(String url, byte[] bytes, ImageView view) {
        if (!url.equals(view.getTag()) || bytes == null || bytes.length == 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
                Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
                view.setImageDrawable(drawable);
                if (drawable instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) drawable).start();
            } else {
                view.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            }
        } catch (Exception e) {
            try { view.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length)); } catch (Exception ignored) {}
        }
    }
}
