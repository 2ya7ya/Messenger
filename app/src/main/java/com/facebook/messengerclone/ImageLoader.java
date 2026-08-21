package com.facebook.messengerclone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A compact Fresco-style pipeline for the app's plain ImageViews. */
final class ImageLoader {
    private static final long MAX_DISK_BYTES=96L*1024L*1024L;
    private final ApiClient api;
    private final File dir;
    private final int defaultTargetPx,screenWidthPx,screenHeightPx;
    private final ExecutorService visibleExecutor=Executors.newFixedThreadPool(3);
    private final ExecutorService prefetchExecutor=Executors.newSingleThreadExecutor();
    private final Map<String,Object> encodedLocks=new ConcurrentHashMap<>();
    private final Map<String,Object> decodeLocks=new ConcurrentHashMap<>();
    private final Map<String,String> bestBitmapKey=new ConcurrentHashMap<>();
    private final Set<String> decodedUrls=ConcurrentHashMap.newKeySet();
    private final LruCache<String,byte[]> encodedMemory=new LruCache<String,byte[]>(8*1024*1024){
        @Override protected int sizeOf(String key,byte[] value){return value==null?0:value.length;}
    };
    private final LruCache<String,Bitmap> bitmapMemory=new LruCache<String,Bitmap>(24*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value==null?0:value.getByteCount();}
    };

    ImageLoader(Context context,ApiClient api){
        this.api=api;
        dir=new File(context.getCacheDir(),"messenger_images");
        if(!dir.exists())dir.mkdirs();
        float density=context.getResources().getDisplayMetrics().density;
        defaultTargetPx=Math.max(256,Math.round(220*density));
        screenWidthPx=context.getResources().getDisplayMetrics().widthPixels;
        screenHeightPx=context.getResources().getDisplayMetrics().heightPixels;
        prefetchExecutor.execute(this::trimDiskCache);
    }

    void load(String url,ImageView view){load(url,view,null);}

    void load(String url,ImageView view,Runnable ready){
        if(url==null||url.isEmpty()){
            view.setTag(null);
            view.setImageDrawable(null);
            return;
        }
        int[] target=targetSize(view);
        String key=bitmapKey(url,target[0],target[1]);
        view.setTag(key);
        Bitmap exact=bitmapMemory.get(key);
        if(exact!=null){
            view.setImageBitmap(exact);
            if(ready!=null)view.post(ready);
            return;
        }
        Bitmap preview=bestBitmap(url);
        if(preview!=null)view.setImageBitmap(preview);
        visibleExecutor.execute(()->{
            Bitmap bitmap=null;
            try{bitmap=decode(url,target[0],target[1]);}catch(Exception ignored){}
            Bitmap result=bitmap;
            view.post(()->{
                if(!key.equals(view.getTag()))return;
                if(result!=null)view.setImageBitmap(result);
                if(result!=null&&ready!=null)ready.run();
            });
        });
    }

    void prefetch(String url){prefetch(url,null);}
    void prefetch(String url,Runnable ready){prefetch(url,defaultTargetPx,defaultTargetPx,ready);}
    void prefetch(String url,int width,int height){prefetch(url,width,height,null);}

    void prefetch(String url,int width,int height,Runnable ready){
        if(url==null||url.isEmpty()){
            if(ready!=null)ready.run();
            return;
        }
        int w=Math.max(1,width),h=Math.max(1,height);
        String key=bitmapKey(url,w,h);
        if(bitmapMemory.get(key)!=null){
            if(ready!=null)ready.run();
            return;
        }
        prefetchExecutor.execute(()->{
            Bitmap bitmap=null;
            try{bitmap=decode(url,w,h);}catch(Exception ignored){}
            if(bitmap!=null&&ready!=null)ready.run();
        });
    }

    boolean isReady(String url){return url!=null&&!url.isEmpty()&&decodedUrls.contains(url)&&bestBitmap(url)!=null;}

    private int[] targetSize(ImageView view){
        int width=0,height=0;
        boolean fullWidth=false,fullHeight=false;
        if(view.getLayoutParams()!=null){width=view.getLayoutParams().width;height=view.getLayoutParams().height;fullWidth=width==-1;fullHeight=height==-1;}
        if(width<=0)width=view.getWidth();
        if(height<=0)height=view.getHeight();
        if(width<=0||width>screenWidthPx)width=fullWidth?screenWidthPx:defaultTargetPx;
        if(height<=0||height>screenHeightPx)height=fullHeight?screenHeightPx:defaultTargetPx;
        return new int[]{Math.max(1,width),Math.max(1,height)};
    }

    private Bitmap bestBitmap(String url){String key=bestBitmapKey.get(url);return key==null?null:bitmapMemory.get(key);}

    private Bitmap decode(String url,int targetWidth,int targetHeight)throws Exception{
        String key=bitmapKey(url,targetWidth,targetHeight);
        Bitmap cached=bitmapMemory.get(key);
        if(cached!=null)return cached;
        Object lock=decodeLocks.computeIfAbsent(key,k->new Object());
        try{
            synchronized(lock){
                cached=bitmapMemory.get(key);
                if(cached!=null)return cached;
                byte[] bytes=getEncoded(url);
                if(bytes==null||bytes.length==0)return null;
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                options.inSampleSize=sampleSize(bounds.outWidth,bounds.outHeight,targetWidth,targetHeight);
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap!=null){bitmapMemory.put(key,bitmap);bestBitmapKey.put(url,key);decodedUrls.add(url);}
                return bitmap;
            }
        }finally{decodeLocks.remove(key,lock);}
    }

    private byte[] getEncoded(String url)throws Exception{
        byte[] cached=encodedMemory.get(url);
        if(cached!=null)return cached;
        Object lock=encodedLocks.computeIfAbsent(url,k->new Object());
        try{
            synchronized(lock){
                cached=encodedMemory.get(url);
                if(cached!=null)return cached;
                byte[] bytes;
                if(url.startsWith("data:image/")){
                    int comma=url.indexOf(',');if(comma<0)return null;
                    bytes=Base64.decode(url.substring(comma+1),Base64.DEFAULT);
                }else if(url.startsWith("file:")){
                    String path=Uri.parse(url).getPath();if(path==null||path.isEmpty())return null;
                    bytes=java.nio.file.Files.readAllBytes(new File(path).toPath());
                }else{
                    String hashed=hash(url);
                    File file=new File(dir,hashed+".bin"),legacy=new File(dir,hashed);
                    if(!file.exists()&&legacy.exists())file=legacy;
                    if(file.exists()&&file.length()>0){
                        bytes=java.nio.file.Files.readAllBytes(file.toPath());
                        file.setLastModified(System.currentTimeMillis());
                    }else{
                        bytes=api.getBytesSync(url);
                        if(bytes!=null&&bytes.length>0){
                            File temporary=new File(dir,file.getName()+".part");
                            try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes);}
                            if(!temporary.renameTo(file)){
                                try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
                                temporary.delete();
                            }
                        }
                    }
                }
                if(bytes!=null&&bytes.length>0)encodedMemory.put(url,bytes);
                return bytes;
            }
        }finally{encodedLocks.remove(url,lock);}
    }

    private static int sampleSize(int sourceWidth,int sourceHeight,int targetWidth,int targetHeight){
        if(sourceWidth<=0||sourceHeight<=0)return 1;
        int sample=1;
        while(sourceWidth/(sample*2)>=targetWidth&&sourceHeight/(sample*2)>=targetHeight)sample*=2;
        return Math.max(1,sample);
    }

    private static String bitmapKey(String url,int width,int height){
        int w=Math.max(64,((width+63)/64)*64),h=Math.max(64,((height+63)/64)*64);
        return url+"#"+w+"x"+h;
    }

    private void trimDiskCache(){
        try{
            File[] files=dir.listFiles((parent,name)->!name.endsWith(".part"));
            if(files==null)return;
            long total=0;for(File file:files)total+=file.length();
            if(total<=MAX_DISK_BYTES)return;
            Arrays.sort(files,Comparator.comparingLong(File::lastModified));
            for(File file:files){
                if(total<=MAX_DISK_BYTES*3/4)break;
                long length=file.length();if(file.delete())total-=length;
            }
        }catch(Exception ignored){}
    }

    private static String hash(String value)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte item:digest)out.append(String.format("%02x",item));return out.toString();
    }
}
