package com.facebook.messengerclone;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import okhttp3.WebSocket;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(8,102,255);
    private static final int TEXT = Color.rgb(5,5,5);
    private static final int SUB = Color.rgb(101,103,107);
    private static final int LIGHT = Color.rgb(240,242,245);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ApiClient api;
    private MessengerCache cache;
    private ImageLoader images;
    private WebSocket socket;
    private FrameLayout root;
    private ListView list;
    private InboxAdapter inboxAdapter;
    private MessageAdapter messageAdapter;
    private final List<JSONObject> inbox = new ArrayList<>();
    private final List<JSONObject> filteredInbox = new ArrayList<>();
    private final List<JSONObject> messages = new ArrayList<>();
    private JSONObject activeConversation;
    private EditText searchBox;
    private EditText messageInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        api = new ApiClient(this);
        cache = new MessengerCache(this);
        images = new ImageLoader(this, api);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        if (api.hasSession()) showInbox(true); else showLogin();
    }

    @Override protected void onDestroy() {
        if (socket != null) socket.close(1000, "bye");
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (activeConversation != null) showInbox(false); else super.onBackPressed();
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float sp, int color, int style) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL);
        if (style != Typeface.NORMAL) t.setTypeface(Typeface.DEFAULT, style); return t;
    }
    private GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }
    private Button icon(String glyph) {
        Button b = new Button(this); b.setText(glyph); b.setTextSize(24); b.setTextColor(TEXT); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setMinWidth(0); b.setMinHeight(0); b.setBackgroundColor(Color.TRANSPARENT);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(40),dp(40))); return b;
    }

    private void showLogin() {
        root.removeAllViews(); activeConversation = null;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL); box.setPadding(dp(26),dp(52),dp(26),dp(24));
        root.addView(box, new FrameLayout.LayoutParams(-1,-1));
        TextView logo = text("Messenger", 31, BLUE, Typeface.BOLD); logo.setGravity(Gravity.CENTER); box.addView(logo,new LinearLayout.LayoutParams(-1,dp(90)));
        EditText id = new EditText(this); id.setHint("Mobile number or email"); id.setSingleLine(true); id.setTextSize(16); id.setPadding(dp(14),0,dp(14),0); id.setBackground(bg(LIGHT,12)); box.addView(id,new LinearLayout.LayoutParams(-1,dp(52)));
        Space s = new Space(this); box.addView(s,new LinearLayout.LayoutParams(1,dp(12)));
        EditText pass = new EditText(this); pass.setHint("Password"); pass.setSingleLine(true); pass.setInputType(0x81); pass.setTextSize(16); pass.setPadding(dp(14),0,dp(14),0); pass.setBackground(bg(LIGHT,12)); box.addView(pass,new LinearLayout.LayoutParams(-1,dp(52)));
        Button login = new Button(this); login.setText("Log in"); login.setTextColor(Color.WHITE); login.setTextSize(16); login.setTypeface(Typeface.DEFAULT,Typeface.BOLD); login.setBackground(bg(BLUE,24)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.topMargin=dp(16); box.addView(login,lp);
        ProgressBar p=new ProgressBar(this); p.setVisibility(View.GONE); box.addView(p,new LinearLayout.LayoutParams(dp(38),dp(38)));
        login.setOnClickListener(v->{
            String a=id.getText().toString().trim(), b=pass.getText().toString(); if(a.isEmpty()||b.isEmpty()){toast("Enter your login details.");return;}
            login.setEnabled(false);p.setVisibility(View.VISIBLE);
            api.login(a,b,(json,error)->main.post(()->{login.setEnabled(true);p.setVisibility(View.GONE);if(error!=null){toast(error.getMessage());return;}showInbox(true);}));
        });
    }

    private void showInbox(boolean initial) {
        activeConversation = null;
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Color.WHITE); root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(8),0,dp(8),0); page.addView(head,new LinearLayout.LayoutParams(-1,dp(58)));
        Button back=icon("‹"); head.addView(back); back.setOnClickListener(v->finish());
        TextView title=text("Chats",25,TEXT,Typeface.BOLD); LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-1,1); head.addView(title,tl);
        Button search=icon("⌕"); head.addView(search); Button compose=icon("+"); compose.setTextSize(28); head.addView(compose);
        searchBox = new EditText(this); searchBox.setSingleLine(true); searchBox.setHint("Search chats"); searchBox.setTextSize(16); searchBox.setPadding(dp(15),0,dp(15),0); searchBox.setBackground(bg(LIGHT,22)); LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(40)); sl.setMargins(dp(12),dp(8),dp(12),dp(5)); page.addView(searchBox,sl);
        list = new ListView(this); list.setDivider(null); list.setSelector(android.R.color.transparent); list.setPadding(dp(8),dp(2),dp(8),dp(90)); list.setClipToPadding(false); page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        inboxAdapter = new InboxAdapter(); list.setAdapter(inboxAdapter); list.setOnItemClickListener((p,v,pos,id)->openConversation(filteredInbox.get(pos)));
        Button fab=new Button(this); fab.setText("+"); fab.setTextColor(Color.WHITE); fab.setTextSize(30); fab.setPadding(0,0,0,dp(2)); fab.setBackground(bg(BLUE,30)); FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.END|Gravity.BOTTOM);fp.setMargins(0,0,dp(18),dp(22));root.addView(fab,fp);
        compose.setOnClickListener(v->showContacts()); fab.setOnClickListener(v->showContacts());
        search.setOnClickListener(v->{searchBox.requestFocus();});
        searchBox.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filterInbox(s.toString());}public void afterTextChanged(Editable e){}});
        loadCachedInbox(); refreshInbox(); connectSocket();
    }

    private void loadCachedInbox() {
        String raw=cache.get("inbox"); if(raw==null)return; try{applyInbox(new JSONObject(raw).optJSONArray("conversations"),false);}catch(Exception ignored){}
    }
    private void refreshInbox() {
        api.get("/api/messaging/inbox?limit=30",(json,error)->main.post(()->{
            if(error!=null){if(!api.hasSession())showLogin();return;} cache.put("inbox",json.toString()); applyInbox(json.optJSONArray("conversations"),true);
        }));
    }
    private void applyInbox(JSONArray arr, boolean network) {
        if(arr==null)return; inbox.clear(); for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o!=null)inbox.add(o);} filterInbox(searchBox==null?"":searchBox.getText().toString());
    }
    private void filterInbox(String q) {
        filteredInbox.clear(); String n=q==null?"":q.toLowerCase(Locale.ROOT).trim(); for(JSONObject c:inbox)if(n.isEmpty()||c.optString("name").toLowerCase(Locale.ROOT).contains(n))filteredInbox.add(c); if(inboxAdapter!=null)inboxAdapter.notifyDataSetChanged();
    }

    private void openConversation(JSONObject c) {
        activeConversation=c; messages.clear(); root.removeAllViews();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.WHITE);root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(7),0,dp(7),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(58)));
        Button back=icon("‹");head.addView(back);back.setOnClickListener(v->showInbox(false));
        View avatar=buildConversationAvatar(c,38);head.addView(avatar,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);np.leftMargin=dp(7);head.addView(names,np);
        names.addView(text(c.optString("name","Conversation"),15,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(28)));
        String status=conversationStatus(c);names.addView(text(status,11,SUB,Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(19)));
        Button info=icon("ⓘ");head.addView(info);
        list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);list.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);list.setStackFromBottom(true);list.setPadding(dp(10),dp(12),dp(10),dp(8));list.setClipToPadding(false);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));messageAdapter=new MessageAdapter();list.setAdapter(messageAdapter);
        TextView typing=text("",11,SUB,Typeface.NORMAL);typing.setPadding(dp(14),0,dp(14),0);page.addView(typing,new LinearLayout.LayoutParams(-1,dp(20)));
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.BOTTOM|Gravity.CENTER_VERTICAL);composer.setPadding(dp(6),dp(5),dp(6),dp(8));page.addView(composer,new LinearLayout.LayoutParams(-1,dp(58)));
        Button attach=icon("＋");attach.setTextColor(BLUE);composer.addView(attach);Button emoji=icon("☺");emoji.setTextColor(BLUE);composer.addView(emoji);
        messageInput=new EditText(this);messageInput.setHint("Message");messageInput.setTextSize(15);messageInput.setMaxLines(4);messageInput.setPadding(dp(13),dp(7),dp(13),dp(7));messageInput.setBackground(bg(LIGHT,20));composer.addView(messageInput,new LinearLayout.LayoutParams(0,dp(40),1));
        Button mic=icon("●");mic.setTextColor(BLUE);composer.addView(mic);Button send=icon("➤");send.setTextColor(BLUE);composer.addView(send);send.setOnClickListener(v->sendText());
        loadCachedMessages(c.optString("id"));refreshMessages(c.optString("id"));
    }

    private void loadCachedMessages(String cid){String raw=cache.get("messages:"+cid);if(raw==null)return;try{applyMessages(new JSONObject(raw).optJSONArray("messages"));}catch(Exception ignored){}}
    private void refreshMessages(String cid){api.get("/api/messaging/conversations/"+cid+"/messages?limit=80",(json,error)->main.post(()->{if(error!=null)return;cache.put("messages:"+cid,json.toString());applyMessages(json.optJSONArray("messages"));}));}
    private void applyMessages(JSONArray arr){if(arr==null)return;messages.clear();for(int i=0;i<arr.length();i++){JSONObject m=arr.optJSONObject(i);if(m!=null)messages.add(m);}if(messageAdapter!=null){messageAdapter.notifyDataSetChanged();if(list!=null)list.post(()->list.setSelection(Math.max(0,messageAdapter.getCount()-1)));}}
    private void sendText(){if(activeConversation==null||messageInput==null)return;String body=messageInput.getText().toString().trim();if(body.isEmpty())return;messageInput.setText("");try{JSONObject req=new JSONObject().put("body",body).put("clientId",UUID.randomUUID().toString());String cid=activeConversation.optString("id");api.post("/api/messaging/conversations/"+cid+"/messages",req,(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject m=json.optJSONObject("message");if(m!=null){messages.add(m);messageAdapter.notifyDataSetChanged();list.setSelection(messageAdapter.getCount()-1);cacheMessagesNow();refreshInbox();}}));}catch(Exception e){toast(e.getMessage());}}
    private void cacheMessagesNow(){try{JSONObject o=new JSONObject().put("messages",new JSONArray(messages));cache.put("messages:"+activeConversation.optString("id"),o.toString());}catch(Exception ignored){}}

    private void showContacts(){
        root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(7),0,dp(7),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(58)));Button back=icon("‹");head.addView(back);back.setOnClickListener(v->showInbox(false));TextView t=text("New message",20,TEXT,Typeface.BOLD);head.addView(t,new LinearLayout.LayoutParams(0,-1,1));
        EditText q=new EditText(this);q.setHint("Search people");q.setSingleLine(true);q.setBackground(bg(LIGHT,22));q.setPadding(dp(15),0,dp(15),0);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(40));qp.setMargins(dp(12),dp(8),dp(12),dp(5));page.addView(q,qp);ListView contacts=new ListView(this);contacts.setDivider(null);page.addView(contacts,new LinearLayout.LayoutParams(-1,0,1));
        final List<JSONObject> data=new ArrayList<>();BaseAdapter a=new BaseAdapter(){public int getCount(){return data.size();}public Object getItem(int p){return data.get(p);}public long getItemId(int p){return p;}public View getView(int p,View cv,ViewGroup parent){JSONObject c=data.get(p);LinearLayout r=new LinearLayout(MainActivity.this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(6),dp(12),dp(6));View av=buildUserAvatar(c.optString("avatar"),c.optString("name"),48);r.addView(av,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView n=text(c.optString("name"),15,TEXT,Typeface.BOLD);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,dp(60),1);nlp.leftMargin=dp(10);r.addView(n,nlp);return r;}};contacts.setAdapter(a);
        Runnable load=()->api.get("/api/messaging/contacts?q="+android.net.Uri.encode(q.getText().toString()),(json,error)->main.post(()->{if(error!=null)return;data.clear();JSONArray ar=json.optJSONArray("contacts");if(ar!=null)for(int i=0;i<ar.length();i++){JSONObject o=ar.optJSONObject(i);if(o!=null)data.add(o);}a.notifyDataSetChanged();}));load.run();q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){main.removeCallbacks(load);main.postDelayed(load,250);}public void afterTextChanged(Editable e){}});
        contacts.setOnItemClickListener((p,v,pos,id)->{JSONObject c=data.get(pos);try{api.post("/api/messaging/conversations",new JSONObject().put("type","direct").put("userId",c.optString("id")),(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject conv=json.optJSONObject("conversation");if(conv!=null)openConversation(conv);}));}catch(Exception e){toast(e.getMessage());}});
    }

    private void connectSocket(){if(socket!=null)return;socket=api.openMessengerSocket(json->main.post(()->handleSocket(json)));}
    private void handleSocket(JSONObject e){String type=e.optString("type");if("conversation".equals(type)||"conversation_update".equals(type)){refreshInbox();return;}if("message".equals(type)){JSONObject m=e.optJSONObject("message");String cid=e.optString("conversationId");if(activeConversation!=null&&activeConversation.optString("id").equals(cid)&&m!=null){boolean exists=false;for(JSONObject x:messages)if(x.optString("id").equals(m.optString("id"))){exists=true;break;}if(!exists){messages.add(m);messageAdapter.notifyDataSetChanged();list.setSelection(messageAdapter.getCount()-1);cacheMessagesNow();}}refreshInbox();}}

    private String conversationStatus(JSONObject c){JSONArray ps=c.optJSONArray("participants");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&!p.optBoolean("isSelf")&&p.optBoolean("online"))return "Active now";}return "";}
    private String time(String value){if(value==null||value.isEmpty())return"";try{String v=value.replace("Z", "+00:00");Date d;try{d=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).parse(v);}catch(Exception ignore){d=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).parse(v);}if(d==null)return"";java.util.Calendar now=java.util.Calendar.getInstance();java.util.Calendar then=java.util.Calendar.getInstance();then.setTime(d);boolean sameDay=now.get(java.util.Calendar.YEAR)==then.get(java.util.Calendar.YEAR)&&now.get(java.util.Calendar.DAY_OF_YEAR)==then.get(java.util.Calendar.DAY_OF_YEAR);return new SimpleDateFormat(sameDay?"h:mm a":"MMM d",Locale.getDefault()).format(d);}catch(Exception e){return"";}}
    private String previewLabel(String t){if("image".equals(t))return"📷 Photo";if("video".equals(t))return"🎬 Video";if("audio".equals(t))return"🎤 Voice message";if("file".equals(t))return"📎 File";return"";}
    private String firstLetter(String name){String n=name==null?"":name.trim();return n.isEmpty()?"?":String.valueOf(Character.toUpperCase(n.charAt(0)));}
    private String avatarValue(JSONObject obj,String key){String v=obj==null?"":obj.optString(key);return (v==null||v.isEmpty()||"null".equalsIgnoreCase(v))?"":v;}
    private String conversationAvatarUrl(JSONObject c){if(c==null)return"";String[] keys={"avatar","groupAvatar","photoUrl","imageUrl","picture","profilePicture"};for(String k:keys){String v=avatarValue(c,k);if(!v.isEmpty())return v;}return "";}
    private JSONObject firstOtherParticipant(JSONObject c){if(c==null)return null;JSONArray ps=c.optJSONArray("participants");if(ps==null)return null;for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&!p.optBoolean("isSelf"))return p;}return null;}
    private View buildUserAvatar(String url,String name,int sizeDp){FrameLayout box=new FrameLayout(this);box.setBackground(bg(Color.rgb(228,230,235),sizeDp/2f));box.setClipToOutline(true);String clean=url==null?"":url.trim();if(!clean.isEmpty()&&!"null".equalsIgnoreCase(clean)){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);box.addView(image,new FrameLayout.LayoutParams(-1,-1));images.load(clean,image);}else{TextView t=text(firstLetter(name),18,Color.rgb(101,103,107),Typeface.BOLD);t.setGravity(Gravity.CENTER);box.addView(t,new FrameLayout.LayoutParams(-1,-1));}return box;}
    private View buildConversationAvatar(JSONObject c,int sizeDp){String avatarUrl=conversationAvatarUrl(c);if(!avatarUrl.isEmpty())return buildUserAvatar(avatarUrl,c.optString("name"),sizeDp);FrameLayout box=new FrameLayout(this);TextView base=text("f",sizeDp*0.56f,Color.WHITE,Typeface.BOLD);base.setGravity(Gravity.CENTER);base.setBackground(bg(BLUE,sizeDp/2f));box.addView(base,new FrameLayout.LayoutParams(-1,-1));JSONObject other=firstOtherParticipant(c);String miniUrl=conversationAvatarUrl(other);String miniName=other==null?c.optString("name"):other.optString("name",c.optString("name"));int miniSize=Math.max(20,Math.round(sizeDp*0.46f));View mini=buildUserAvatar(miniUrl,miniName,miniSize);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(miniSize),dp(miniSize),Gravity.END|Gravity.BOTTOM);box.addView(mini,mp);return box;}
    private void toast(String s){Toast.makeText(this,s==null?"Something went wrong":s,Toast.LENGTH_SHORT).show();}

    private final class InboxAdapter extends BaseAdapter {
        public int getCount(){return filteredInbox.size();}public Object getItem(int p){return filteredInbox.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View cv,ViewGroup parent){JSONObject c=filteredInbox.get(p);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(7),dp(8),dp(7));row.setMinimumHeight(dp(72));View av=buildConversationAvatar(c,56);row.addView(av,new LinearLayout.LayoutParams(dp(56),dp(56)));LinearLayout copy=new LinearLayout(MainActivity.this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(58),1);cp.leftMargin=dp(10);row.addView(copy,cp);LinearLayout titleRow=new LinearLayout(MainActivity.this);titleRow.setGravity(Gravity.CENTER_VERTICAL);copy.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(27)));TextView name=text(c.optString("name","Conversation"),15.5f,TEXT,Typeface.BOLD);titleRow.addView(name,new LinearLayout.LayoutParams(-2,-1));if(c.optBoolean("isPinned")||c.optBoolean("pinned")){TextView pin=text("📌",12,Color.rgb(188,192,200),Typeface.NORMAL);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,-1);pp.leftMargin=dp(4);titleRow.addView(pin,pp);}JSONObject last=c.optJSONObject("lastMessage");String preview=last==null?"No messages yet":last.optString("body");if((preview==null||preview.isEmpty())&&last!=null)preview=previewLabel(last.optString("type","text"));if(preview==null||preview.isEmpty())preview="No messages yet";TextView pr=text(preview,13.5f,c.optInt("unread")>0?TEXT:SUB,c.optInt("unread")>0?Typeface.BOLD:Typeface.NORMAL);pr.setSingleLine(true);pr.setEllipsize(TextUtils.TruncateAt.END);copy.addView(pr,new LinearLayout.LayoutParams(-1,dp(26)));LinearLayout meta=new LinearLayout(MainActivity.this);meta.setOrientation(LinearLayout.VERTICAL);meta.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);row.addView(meta,new LinearLayout.LayoutParams(dp(64),dp(58)));TextView tm=text(last==null?"":time(last.optString("createdAt")),10.5f,SUB,Typeface.NORMAL);tm.setGravity(Gravity.END);tm.setSingleLine(true);meta.addView(tm,new LinearLayout.LayoutParams(-1,dp(20)));int unread=c.optInt("unread");if(unread>0){TextView badge=text(String.valueOf(unread),10,Color.WHITE,Typeface.BOLD);badge.setGravity(Gravity.CENTER);badge.setBackground(bg(BLUE,10));LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(dp(unread>9?22:18),dp(18));blp.topMargin=dp(4);meta.addView(badge,blp);}return row;}
    }

    private final class MessageAdapter extends BaseAdapter {
        public int getCount(){return messages.size();}public Object getItem(int p){return messages.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View cv,ViewGroup parent){JSONObject m=messages.get(p);boolean mine=m.optJSONObject("sender")!=null&&m.optJSONObject("sender").optBoolean("isSelf",false);if(!mine&&activeConversation!=null){JSONArray ps=activeConversation.optJSONArray("participants");String sid=m.optString("senderId");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject x=ps.optJSONObject(i);if(x!=null&&sid.equals(x.optString("id"))&&x.optBoolean("isSelf")){mine=true;break;}}}
            LinearLayout outer=new LinearLayout(MainActivity.this);outer.setOrientation(LinearLayout.VERTICAL);outer.setGravity(mine?Gravity.END:Gravity.START);outer.setPadding(dp(3),dp(2),dp(3),dp(2));String body=m.optString("body");String type=m.optString("type","text");TextView bubble=text(body.isEmpty()?labelForType(type):body,15,mine?Color.WHITE:TEXT,Typeface.NORMAL);bubble.setGravity(Gravity.CENTER_VERTICAL);bubble.setPadding(dp(12),dp(9),dp(12),dp(9));bubble.setBackground(bg(mine?BLUE:Color.rgb(228,230,235),18));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.gravity=mine?Gravity.END:Gravity.START;bp.width=Math.min(dp(310),getResources().getDisplayMetrics().widthPixels-dp(72));outer.addView(bubble,bp);if(mine&&p==messages.size()-1){TextView st=text(m.optString("status","sent"),9,Color.rgb(138,141,145),Typeface.NORMAL);st.setGravity(Gravity.END);outer.addView(st,new LinearLayout.LayoutParams(-2,dp(14)));}return outer;}
        private String labelForType(String t){return previewLabel(t);}
    }
}
