package com.facebook.messengerclone;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class ApiClient {
    interface JsonCallback { void done(JSONObject json, Exception error); }
    interface SocketCallback { void event(JSONObject json); }
    interface SocketClosedCallback { void closed(); }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final String baseUrl = BuildConfig.BASE_URL.replaceAll("/$", "");
    private final SharedPreferences prefs;
    private final OkHttpClient http;

    ApiClient(Context context) {
        prefs = context.getSharedPreferences("messenger_native_auth", Context.MODE_PRIVATE);
        http = new OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build();
    }

    boolean hasSession() { return !prefs.getString("cookie", "").isEmpty(); }
    void clearSession() { prefs.edit().remove("cookie").apply(); }
    String absolute(String path) { if (path == null || path.isEmpty()) return ""; if (path.startsWith("http://") || path.startsWith("https://")) return path; return baseUrl + (path.startsWith("/") ? path : "/" + path); }
    Map<String,String> mediaHeaders(){ Map<String,String> m=new HashMap<>(); String cookie=prefs.getString("cookie",""); if(!cookie.isEmpty())m.put("Cookie",cookie); return m; }

    private Request.Builder request(String path) {
        Request.Builder b = new Request.Builder().url(absolute(path)); String cookie = prefs.getString("cookie", ""); if (!cookie.isEmpty()) b.header("Cookie", cookie); b.header("Accept", "application/json"); return b;
    }

    void login(String identifier, String password, JsonCallback cb) {
        try {
            JSONObject body = new JSONObject().put("identifier", identifier).put("password", password);
            Request req = request("/api/login").post(RequestBody.create(body.toString(), JSON)).build();
            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { cb.done(null, e); }
                @Override public void onResponse(Call call, Response response) { try (response) { String text=response.body()==null?"{}":response.body().string(); JSONObject json=new JSONObject(text.isEmpty()?"{}":text); if(!response.isSuccessful()){cb.done(json,new IOException(json.optString("error","Login failed")));return;} saveCookies(response.headers()); cb.done(json,null); } catch(Exception e){cb.done(null,e);} }
            });
        } catch (Exception e) { cb.done(null, e); }
    }

    void get(String path, JsonCallback cb) { execute(request(path).get().build(), cb); }
    void post(String path, JSONObject body, JsonCallback cb) { execute(request(path).post(RequestBody.create(body==null?"{}":body.toString(),JSON)).build(), cb); }
    void patch(String path, JSONObject body, JsonCallback cb) { execute(request(path).patch(RequestBody.create(body==null?"{}":body.toString(),JSON)).build(), cb); }
    void delete(String path, JsonCallback cb) { execute(request(path).delete().build(), cb); }

    void upload(String path, byte[] bytes, String fileName, String mimeType, String caption, String clientId, String replyToId, JsonCallback cb) {
        try {
            String actualMime=(mimeType==null||mimeType.isEmpty())?"application/octet-stream":mimeType;
            RequestBody body=RequestBody.create(bytes, MediaType.get("application/octet-stream"));
            Request.Builder b=request(path).post(body)
                    .header("Content-Type", "application/octet-stream")
                    .header("X-File-Name", URLEncoder.encode(fileName==null?"attachment":fileName, StandardCharsets.UTF_8))
                    .header("X-File-Type", actualMime)
                    .header("X-Client-Id", clientId==null?"":clientId)
                    .header("X-Caption", URLEncoder.encode(caption==null?"":caption, StandardCharsets.UTF_8));
            if(replyToId!=null&&!replyToId.isEmpty())b.header("X-Reply-To-Id",replyToId);
            execute(b.build(),cb);
        } catch(Exception e){ cb.done(null,e); }
    }

    private void execute(Request req, JsonCallback cb) {
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { cb.done(null, e); }
            @Override public void onResponse(Call call, Response response) { try(response){ String text=response.body()==null?"{}":response.body().string(); JSONObject json=new JSONObject(text.isEmpty()?"{}":text); if(response.code()==401||(response.code()==403&&json.optString("error").toLowerCase().contains("sign")))clearSession(); if(!response.isSuccessful())cb.done(json,new IOException(json.optString("error","Request failed"))); else cb.done(json,null); }catch(Exception e){cb.done(null,e);} }
        });
    }

    byte[] getBytesSync(String path) throws IOException { try(Response response=http.newCall(request(path).get().build()).execute()){ if(!response.isSuccessful()||response.body()==null)throw new IOException("Request failed"); return response.body().bytes(); } }

    WebSocket openMessengerSocket(SocketCallback cb, SocketClosedCallback closed) {
        String ws=baseUrl.replaceFirst("^https://","wss://").replaceFirst("^http://","ws://")+"/ws/messenger"; Request.Builder b=new Request.Builder().url(ws); String cookie=prefs.getString("cookie",""); if(!cookie.isEmpty())b.header("Cookie",cookie);
        return http.newWebSocket(b.build(),new WebSocketListener(){
            private boolean ended=false;
            private void finish(){if(ended)return;ended=true;if(closed!=null)closed.closed();}
            @Override public void onMessage(WebSocket webSocket,String text){try{cb.event(new JSONObject(text));}catch(Exception ignored){}}
            @Override public void onClosed(WebSocket webSocket,int code,String reason){finish();}
            @Override public void onFailure(WebSocket webSocket,Throwable t,Response response){finish();}
        });
    }

    private void saveCookies(Headers headers) { List<String> cookies=headers.values("Set-Cookie"); String session=""; for(String c:cookies){ if(c.startsWith("facebook_session=")){session=c.substring(0,c.indexOf(';')>0?c.indexOf(';'):c.length());break;} } if(!session.isEmpty())prefs.edit().putString("cookie",session).apply(); }
}
