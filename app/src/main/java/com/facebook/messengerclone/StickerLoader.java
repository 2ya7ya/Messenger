package com.facebook.messengerclone;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class StickerLoader {
    private final ApiClient api;
    private final File dir;
    private final Resources resources;
    private final AtomicLong taskOrder=new AtomicLong();
    private final ThreadPoolExecutor loaderExecutor=new ThreadPoolExecutor(1,1,20L,TimeUnit.SECONDS,new PriorityBlockingQueue<>());
    private final Map<String,Object> locks=new ConcurrentHashMap<>();
    private final Object decodeLock=new Object();
    private final Map<String,List<Target>> decodeWaiters=new ConcurrentHashMap<>();
    private final LruCache<String,byte[]> memory=new LruCache<String,byte[]>(12*1024*1024){
        @Override protected int sizeOf(String key,byte[] value){return value==null?0:value.length;}
    };
    private final LruCache<String,Drawable.ConstantState> decoded=new LruCache<>(48);

    private static final class Target{
        final ImageView view;final Runnable ready;
        Target(ImageView v,Runnable r){view=v;ready=r;}
    }

    StickerLoader(Context context,ApiClient api){
        this.api=api;
        resources=context.getResources();
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

    boolean isReady(String url){return url!=null&&!url.isEmpty()&&decoded.get(url)!=null;}

    void load(String url,ImageView view,Runnable ready){
        view.setTag(url);
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if(url==null||url.isEmpty()){view.setImageDrawable(null);if(ready!=null)view.post(ready);return;}
        Drawable.ConstantState cachedState=decoded.get(url);
        if(cachedState!=null){Drawable cached=cachedState.newDrawable(resources);if(setDrawable(url,cached,view)&&ready!=null)ready.run();return;}
        boolean startDecode=false;
        synchronized(decodeLock){
            List<Target> targets=decodeWaiters.get(url);
            if(targets==null){targets=new ArrayList<>();decodeWaiters.put(url,targets);startDecode=true;}
            targets.add(new Target(view,ready));
        }
        if(!startDecode)return;
        submit(0,()->{
            Drawable drawable=null;Drawable.ConstantState state=null;
            try{
                byte[] bytes=getCachedOrFetch(url);
                if(bytes==null||bytes.length==0)throw new IllegalStateException("Empty sticker");
                if(Build.VERSION.SDK_INT>=28){
                    ImageDecoder.Source source=ImageDecoder.createSource(ByteBuffer.wrap(bytes));
                    drawable=ImageDecoder.decodeDrawable(source,(decoder,info,src)->decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
                }else{
                    android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    if(bitmap!=null)drawable=new BitmapDrawable(resources,bitmap);
                }
                if(drawable!=null){state=drawable.getConstantState();if(state!=null)decoded.put(url,state);}
            }catch(Exception ignored){}
            final Drawable first=drawable;final Drawable.ConstantState reusable=state;final List<Target> targets;
            synchronized(decodeLock){targets=decodeWaiters.remove(url);}
            if(targets==null)return;
            for(int i=0;i<targets.size();i++){Target target=targets.get(i);final Drawable item=reusable==null?first:reusable.newDrawable(resources);target.view.post(()->{if(setDrawable(url,item,target.view)&&target.ready!=null)target.ready.run();});}
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
