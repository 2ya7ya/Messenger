package com.facebook.messengerclone;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class StickerLoader {
    private final ApiClient api;
    private final File dir;
    private final AtomicLong taskOrder=new AtomicLong();
    private final ThreadPoolExecutor loaderExecutor=new ThreadPoolExecutor(1,1,20L,TimeUnit.SECONDS,new PriorityBlockingQueue<>());
    private final Map<String,Object> locks=new ConcurrentHashMap<>();
    private final LruCache<String,byte[]> memory=new LruCache<String,byte[]>(12*1024*1024){
        @Override protected int sizeOf(String key,byte[] value){return value==null?0:value.length;}
    };

    StickerLoader(Context context,ApiClient api){
        this.api=api;
        dir=new File(context.getCacheDir(),"messenger_stickers");
        if(!dir.exists())dir.mkdirs();
        loaderExecutor.allowCoreThreadTimeOut(true);
    }

    private final class PriorityTask implements Runnable,Comparable<PriorityTask>{final int priority;final long order;final Runnable action;PriorityTask(int p,Runnable r){priority=p;order=taskOrder.getAndIncrement();action=r;}public void run(){action.run();}public int compareTo(PriorityTask other){int byPriority=Integer.compare(priority,other.priority);return byPriority!=0?byPriority:Long.compare(order,other.order);}}
    private void submit(int priority,Runnable action){loaderExecutor.execute(new PriorityTask(priority,action));}

    byte[] getCachedOrFetch(String url)throws Exception{
        byte[] cached=memory.get(url);
        if(cached!=null)return cached;
        Object lock=locks.computeIfAbsent(url,k->new Object());
        try{
            synchronized(lock){
                cached=memory.get(url);
                if(cached!=null)return cached;
                File file=new File(dir,hash(url)+".bin");
                byte[] bytes;
                if(file.exists()&&file.length()>0){
                    bytes=java.nio.file.Files.readAllBytes(file.toPath());
                    file.setLastModified(System.currentTimeMillis());
                }else{
                    bytes=api.getBytesSync(url);
                    if(bytes!=null&&bytes.length>0)try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
                }
                if(bytes!=null&&bytes.length>0)memory.put(url,bytes);
                return bytes;
            }
        }finally{locks.remove(url,lock);}
    }

    void prefetch(String url){
        if(url==null||url.isEmpty()||memory.get(url)!=null)return;
        submit(10,()->{try{getCachedOrFetch(url);}catch(Exception ignored){}});
    }

    void load(String url,ImageView view){load(url,view,null);}

    void load(String url,ImageView view,Runnable ready){
        view.setTag(url);
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if(url==null||url.isEmpty()){view.setImageDrawable(null);if(ready!=null)view.post(ready);return;}
        submit(0,()->{
            try{
                byte[] bytes=getCachedOrFetch(url);
                if(bytes==null||bytes.length==0)return;
                if(Build.VERSION.SDK_INT>=28){
                    ImageDecoder.Source source=ImageDecoder.createSource(ByteBuffer.wrap(bytes));
                    Drawable drawable=ImageDecoder.decodeDrawable(source,(decoder,info,src)->decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
                    view.post(()->{if(setDrawable(url,drawable,view)&&ready!=null)ready.run();});
                }else{
                    android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    view.post(()->{if(url.equals(view.getTag())){view.setImageBitmap(bitmap);if(ready!=null)ready.run();}});
                }
            }catch(Exception ignored){}
        });
    }

    private boolean setDrawable(String url,Drawable drawable,ImageView view){
        if(!url.equals(view.getTag())||drawable==null)return false;
        view.setImageDrawable(drawable);
        if(drawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)drawable).start();
        return true;
    }

    private static String hash(String value)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte item:digest)out.append(String.format("%02x",item));return out.toString();
    }
}
