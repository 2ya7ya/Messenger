package com.facebook.messengerclone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Base64;

import okhttp3.WebSocket;

public class MainActivity extends Activity {
    private static final int BLUE=Color.rgb(8,102,255), TEXT=Color.rgb(5,5,5), SUB=Color.rgb(101,103,107), LIGHT=Color.rgb(240,242,245), RECEIVED=Color.rgb(240,242,245), DIV=Color.rgb(228,230,235);
    private static final int PICK_FILE=4021, REQ_MIC=4022, PICK_GROUP_IMAGE=4023, REQ_MEDIA=4024, REQ_CAMERA=4025;
    private static final long BURST_MS=5*60*1000L, STAMP_MS=15*60*1000L;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ApiClient api; private MessengerCache cache; private ImageLoader images; private StickerLoader stickers; private WebSocket socket;
    private FrameLayout root; private ListView list; private View olderMessagesLoaderRow; private OlderMessagesSpinner olderMessagesSpinner; private InboxAdapter inboxAdapter; private MessageAdapter messageAdapter;
    private final List<JSONObject> inbox=new ArrayList<>(), filteredInbox=new ArrayList<>(), messages=new ArrayList<>();
    private final List<JSONObject> socketMessageBatch=new ArrayList<>();
    private final Set<String> typingConversations=new HashSet<>();
    private final Set<String> stickerLastConversations=new HashSet<>();
    private final Map<String,Runnable> typingExpiry=new HashMap<>();
    private final Map<String,Long> localSeenAt=new HashMap<>();
    private final Map<String,List<Runnable>> temporaryVideoWaiters=new HashMap<>();
    private final Map<String,String> temporaryVideoFiles=new HashMap<>();
    private final Set<String> temporaryVideoLoading=new HashSet<>();
    private final ExecutorService temporaryMediaExecutor=Executors.newFixedThreadPool(2);
    private JSONObject activeConversation, replyTo; private EditText searchBox, messageInput; private TextView typingView; private LinearLayout replyBar, composer, recordBar;
    private int socketRetry=0; private boolean socketConnecting=false;
    private static final String COMMENT_STICKER_API_KEY="PvlaAZvthRs8jWekpX4blV5ORIDrykTm";
    private final Runnable socketReconnect=()->{ if(api!=null&&api.hasSession()) connectSocket(); };
    private ImageButton sendButton, micButton; private String selfId=""; private boolean refreshingMessages=false;
    private boolean composerHasText=false, typingStateSent=false, typingStartQueued=false;
    private boolean forceFullScreenMediaPicker=false;
    // 0 = normal composer picker, 1 = camera, 2 = Aa creator.
    private int mediaPickerPreviewOrigin=0;
    private String previewTextPrefill="";
    private long lastTypingWireAt=0L;
    private float timeRevealOffset=0f,timeRevealDownX=Float.NaN,timeRevealDownY=Float.NaN;
    private boolean timeRevealDragging=false;
    private MediaRecorder recorder; private File recordFile; private long recordStarted; private Runnable recordTicker; private float recordDownX,recordDownY; private boolean recordLocked=false,recordCanceled=false,pendingMicStart=false;
    private ImageButton recordCancelButton; private TextView recordCancelHint; private final float[] recordLevels=new float[34];
    private FrameLayout recordOverlay; private View recordLockIndicator; private TextView recordLockHint; private boolean recordLockReady=false,recordDeleteHot=false,recordLockHot=false;
    private String beforeCursor=""; private boolean loadingOlderMessages=false; private JSONObject groupEditConversation; private String groupEditImageData="", groupEditEmoji="", groupEditColor="#3da9ef", groupEditDraftName="";
    private boolean conversationInitialScrollPending=false;
    private String socketMessageBatchConversationId="";
    private final Runnable flushSocketMessages=this::flushSocketMessageBatch;
    private boolean scheduledMessageRefreshKeepsBottom=false;
    private final Runnable bottomAnchor=()->{if(list!=null&&list.getCount()>0)list.setSelection(Math.max(0,list.getCount()-1));};
    private final Runnable messageRefresh=()->{boolean keepBottom=scheduledMessageRefreshKeepsBottom;scheduledMessageRefreshKeepsBottom=false;if(messageAdapter!=null){messageAdapter.notifyDataSetChanged();if(keepBottom)scrollToAbsoluteBottom();}};

    @Override protected void onCreate(Bundle state){super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);api=new ApiClient(this);cache=new MessengerCache(this);images=new ImageLoader(this,api);stickers=new StickerLoader(this,api);root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);setContentView(root);if(api.hasSession())showInbox(true);else showLogin();}
    @Override protected void onResume(){super.onResume();if(api!=null&&api.hasSession())connectSocket();}
    @Override protected void onPause(){
        if(activeConversation!=null&&messageInput!=null){
            messageInput.clearFocus();
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.hideSoftInputFromWindow(messageInput.getWindowToken(),0);
        }
        super.onPause();
    }
    @Override protected void onDestroy(){main.removeCallbacks(socketReconnect);main.removeCallbacks(bottomAnchor);main.removeCallbacks(messageRefresh);if(socket!=null)socket.close(1000,"bye");socket=null;temporaryMediaExecutor.shutdownNow();stopRecorder(false);super.onDestroy();}
    @Override public void onBackPressed(){
        if(root!=null){
            View mediaSheet=root.findViewWithTag("messenger-native-media-sheet");
            if(mediaSheet!=null){
                dismissInstagramMediaPicker(mediaSheet);
                return;
            }

            View stickerSheet=root.findViewWithTag("messenger-native-sticker-sheet");
            if(stickerSheet!=null){
                dismissMessengerStickerPicker(stickerSheet);
                return;
            }
        }
        if(activeConversation!=null)showInbox(false);
        else super.onBackPressed();
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}    
    private GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private void styleColorSwatch(View swatch,int color,boolean selected){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);g.setStroke(dp(selected?3:1),selected?Color.WHITE:Color.argb(105,255,255,255));swatch.setBackground(g);swatch.setScaleX(selected?1.08f:1f);swatch.setScaleY(selected?1.08f:1f);swatch.setElevation(selected?dp(3):0);}
    private GradientDrawable topBg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);float r=dp(radius);g.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});return g;}
    private String activeTheme(){return activeConversation==null?"default":activeConversation.optString("theme","default");}
    private int themeAccent(){switch(activeTheme()){case"instagram":return Color.WHITE;case"instagram-classic":return Color.rgb(162,59,234);case"love":return Color.rgb(255,44,145);case"ocean":return Color.rgb(0,159,200);case"sunset":return Color.rgb(255,108,43);case"monochrome":return Color.rgb(34,34,34);case"glow-pup":return Color.rgb(166,140,255);case"odyssey":return Color.rgb(108,186,186);case"supergirl":return Color.rgb(255,156,32);case"avatar":return Color.rgb(129,181,165);case"olivia":return Color.rgb(255,171,195);case"backrooms":return Color.rgb(211,189,100);case"deli-boys":return Color.rgb(232,203,182);case"heart-drive":return Color.rgb(200,168,255);case"valentines":return Color.rgb(207,43,230);default:return BLUE;}}
    private int themeDisabled(){switch(activeTheme()){case"instagram":return Color.rgb(142,142,142);case"instagram-classic":return Color.rgb(184,166,200);case"love":return Color.rgb(185,138,164);case"ocean":return Color.rgb(129,174,185);case"sunset":return Color.rgb(197,160,140);case"monochrome":return Color.rgb(153,153,153);case"glow-pup":return Color.rgb(113,106,145);case"odyssey":return Color.rgb(98,128,130);case"supergirl":return Color.rgb(158,121,87);case"avatar":return Color.rgb(104,127,120);case"olivia":return Color.rgb(159,120,133);case"backrooms":return Color.rgb(130,122,85);case"deli-boys":return Color.rgb(141,130,121);case"heart-drive":return Color.rgb(120,109,147);case"valentines":return Color.rgb(128,91,134);default:return Color.rgb(169,185,209);}}
    private Bitmap themedIconBitmap(int res,int color){Bitmap src=BitmapFactory.decodeResource(getResources(),res);if(src==null)return null;Bitmap out=src.copy(Bitmap.Config.ARGB_8888,true);for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++){int px=out.getPixel(x,y),a=Color.alpha(px);if(a<20)continue;int r=Color.red(px),g=Color.green(px),b=Color.blue(px);if(r>238&&g>238&&b>238)continue;out.setPixel(x,y,Color.argb(a,Color.red(color),Color.green(color),Color.blue(color)));}return out;}
    private int themeReceived(){switch(activeTheme()){case"instagram":return Color.rgb(38,38,38);case"monochrome":return Color.WHITE;case"glow-pup":return Color.rgb(36,44,88);case"odyssey":return Color.rgb(23,62,66);case"supergirl":return Color.rgb(58,43,39);case"avatar":return Color.rgb(37,62,60);case"olivia":return Color.rgb(33,30,32);case"backrooms":return Color.rgb(56,53,31);case"deli-boys":return Color.rgb(81,76,70);case"heart-drive":return Color.rgb(74,39,157);case"valentines":return Color.rgb(29,11,69);default:return RECEIVED;}}
    private int themeReceivedText(){switch(activeTheme()){case"instagram":case"glow-pup":case"odyssey":case"supergirl":case"avatar":case"olivia":case"backrooms":case"deli-boys":case"heart-drive":case"valentines":return Color.WHITE;default:return TEXT;}}
    private int themeSentText(){switch(activeTheme()){case"olivia":return Color.rgb(43,22,32);case"deli-boys":return Color.rgb(33,29,24);case"heart-drive":return Color.rgb(32,18,61);default:return Color.WHITE;}}
    private int[] themeSentColors(){switch(activeTheme()){case"instagram":return new int[]{Color.rgb(92,16,238),Color.rgb(172,0,238)};case"instagram-classic":return new int[]{Color.rgb(131,58,180),Color.rgb(253,29,29),Color.rgb(252,176,69)};case"love":return new int[]{Color.rgb(255,77,141),Color.rgb(255,117,140)};case"ocean":return new int[]{Color.rgb(0,131,176),Color.rgb(0,180,219)};case"sunset":return new int[]{Color.rgb(255,81,47),Color.rgb(240,152,25)};case"monochrome":return new int[]{Color.rgb(17,17,17),Color.rgb(85,85,85)};case"glow-pup":return new int[]{Color.rgb(118,98,255),Color.rgb(178,143,255)};case"odyssey":return new int[]{Color.rgb(23,107,124),Color.rgb(92,170,169)};case"supergirl":return new int[]{Color.rgb(220,37,45),Color.rgb(255,153,23)};case"avatar":return new int[]{Color.rgb(64,123,119),Color.rgb(131,186,168)};case"olivia":return new int[]{Color.rgb(255,156,186),Color.rgb(255,191,209)};case"backrooms":return new int[]{Color.rgb(141,125,57),Color.rgb(212,188,95)};case"deli-boys":return new int[]{Color.rgb(241,220,203),Color.rgb(248,234,222)};case"heart-drive":return new int[]{Color.rgb(216,195,255),Color.rgb(240,230,255)};case"valentines":return new int[]{Color.rgb(158,33,228),Color.rgb(214,9,189)};default:return new int[]{BLUE,BLUE};}}
    private android.graphics.drawable.Drawable themeConversationBackground(){int[] colors;switch(activeTheme()){case"instagram":return new android.graphics.drawable.ColorDrawable(Color.BLACK);case"instagram-classic":colors=new int[]{Color.rgb(255,245,251),Color.rgb(238,232,255),Color.rgb(234,245,255)};break;case"love":colors=new int[]{Color.rgb(118,0,95),Color.rgb(39,0,45)};break;case"ocean":colors=new int[]{Color.rgb(223,248,255),Color.rgb(216,237,244),Color.rgb(237,250,255)};break;case"sunset":colors=new int[]{Color.rgb(255,243,222),Color.rgb(255,231,223),Color.rgb(255,248,236)};break;case"monochrome":colors=new int[]{Color.rgb(244,244,244),Color.rgb(222,222,222)};break;case"glow-pup":colors=new int[]{Color.rgb(3,0,25),Color.rgb(23,16,92),Color.rgb(33,19,173)};break;case"odyssey":colors=new int[]{Color.rgb(3,28,33),Color.rgb(10,59,66),Color.rgb(8,38,44)};break;case"supergirl":colors=new int[]{Color.rgb(17,17,17),Color.rgb(43,24,19),Color.rgb(73,23,15)};break;case"avatar":colors=new int[]{Color.rgb(16,44,45),Color.rgb(37,81,77),Color.rgb(113,110,77)};break;case"olivia":colors=new int[]{Color.rgb(61,41,55),Color.rgb(121,85,106),Color.rgb(83,97,65)};break;case"backrooms":colors=new int[]{Color.rgb(64,58,24),Color.rgb(119,108,46),Color.rgb(48,44,20)};break;case"deli-boys":colors=new int[]{Color.rgb(23,22,18),Color.rgb(42,39,31),Color.rgb(22,21,18)};break;case"heart-drive":colors=new int[]{Color.rgb(6,19,83),Color.rgb(38,18,113),Color.rgb(8,14,68)};break;case"valentines":colors=new int[]{Color.rgb(61,11,112),Color.rgb(19,5,47)};break;default:return new android.graphics.drawable.ColorDrawable(Color.WHITE);}return new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);}

    private GradientDrawable bubbleBg(boolean mine,boolean samePrev,boolean sameNext){float r=dp(18),small=dp(6);float tl=r,tr=r,br=r,bl=r;if(mine){if(samePrev)tr=small;if(sameNext)br=small;}else{if(samePrev)tl=small;if(sameNext)bl=small;}GradientDrawable g;if(mine&&!"default".equals(activeTheme())){g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,themeSentColors());}else{g=new GradientDrawable();g.setColor(mine?BLUE:themeReceived());}g.setCornerRadii(new float[]{tl,tl,tr,tr,br,br,bl,bl});return g;}
    private TextView text(String v,float sp,int color,int style){TextView t=new TextView(this);t.setText(v==null?"":v);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(style!=Typeface.NORMAL)t.setTypeface(Typeface.DEFAULT,style);return t;}
    private ImageButton icon(int drawable,int sizeDp,int tint){ImageButton b=new ImageButton(this);b.setImageResource(drawable);b.setColorFilter(tint);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(dp(7),dp(7),dp(7),dp(7));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp),dp(sizeDp)));return b;}

    private void showLogin(){root.removeAllViews();activeConversation=null;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(26),dp(52),dp(26),dp(24));root.addView(box,new FrameLayout.LayoutParams(-1,-1));TextView logo=text("Messenger",31,BLUE,Typeface.BOLD);logo.setGravity(Gravity.CENTER);box.addView(logo,new LinearLayout.LayoutParams(-1,dp(90)));EditText id=new EditText(this);id.setHint("Mobile number or email");id.setSingleLine(true);id.setTextSize(16);id.setPadding(dp(14),0,dp(14),0);id.setBackground(bg(LIGHT,12));box.addView(id,new LinearLayout.LayoutParams(-1,dp(52)));Space sp=new Space(this);box.addView(sp,new LinearLayout.LayoutParams(1,dp(12)));EditText pass=new EditText(this);pass.setHint("Password");pass.setSingleLine(true);pass.setInputType(0x81);pass.setTextSize(16);pass.setPadding(dp(14),0,dp(14),0);pass.setBackground(bg(LIGHT,12));box.addView(pass,new LinearLayout.LayoutParams(-1,dp(52)));Button login=new Button(this);login.setText("Log in");login.setTextColor(Color.WHITE);login.setTextSize(16);login.setTypeface(Typeface.DEFAULT,Typeface.BOLD);login.setBackground(bg(BLUE,24));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(16);box.addView(login,lp);ProgressBar p=new ProgressBar(this);p.setVisibility(View.GONE);box.addView(p,new LinearLayout.LayoutParams(dp(48),dp(38)));login.setOnClickListener(v->{String a=id.getText().toString().trim(),b=pass.getText().toString();if(a.isEmpty()||b.isEmpty()){toast("Enter your login details.");return;}login.setEnabled(false);p.setVisibility(View.VISIBLE);api.login(a,b,(json,error)->main.post(()->{login.setEnabled(true);p.setVisibility(View.GONE);if(error!=null){toast(error.getMessage());return;}showInbox(true);}));});}

    private void showInbox(boolean initial){activeConversation=null;replyTo=null;root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.WHITE);root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout head=new LinearLayout(this);head.setBackground(themeConversationBackground());head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(15),0,dp(12),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(58)));TextView title=text("Chats",25,TEXT,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-1,1));ImageButton search=icon(R.drawable.ic_msg_search,40,TEXT);head.addView(search);ImageButton plus=icon(R.drawable.ic_msg_plus,40,TEXT);head.addView(plus);
        searchBox=new EditText(this);searchBox.setSingleLine(true);searchBox.setHint("Search chats");searchBox.setTextSize(16);searchBox.setPadding(dp(15),0,dp(15),0);searchBox.setBackground(bg(LIGHT,22));LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(40));sl.setMargins(dp(12),dp(8),dp(12),dp(5));page.addView(searchBox,sl);list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);list.setVerticalScrollBarEnabled(false);list.setPadding(dp(8),dp(2),dp(8),dp(90));list.setClipToPadding(false);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));inboxAdapter=new InboxAdapter();list.setAdapter(inboxAdapter);list.setOnItemClickListener((p,v,pos,id)->openConversation(filteredInbox.get(pos)));
        ImageButton fab=icon(R.drawable.ic_msg_plus,54,Color.WHITE);fab.setPadding(dp(14),dp(14),dp(14),dp(14));fab.setBackground(bg(BLUE,30));FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.END|Gravity.BOTTOM);fp.setMargins(0,0,dp(18),dp(22));root.addView(fab,fp);plus.setOnClickListener(v->showContacts());fab.setOnClickListener(v->showContacts());search.setOnClickListener(v->searchBox.requestFocus());searchBox.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filterInbox(s.toString());}public void afterTextChanged(Editable e){}});loadCachedInbox();refreshInbox();connectSocket();}
    private void loadCachedInbox(){String raw=cache.get("inbox");if(raw==null)return;try{applyInbox(new JSONObject(raw).optJSONArray("conversations"));}catch(Exception ignored){}}
    private void refreshInbox(){api.get("/api/messaging/inbox?limit=30",(json,error)->main.post(()->{if(error!=null){if(!api.hasSession())showLogin();return;}cache.put("inbox",json.toString());applyInbox(json.optJSONArray("conversations"));}));}
    private void applyInbox(JSONArray arr){if(arr==null)return;inbox.clear();for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o!=null)inbox.add(o);}filterInbox(searchBox==null?"":searchBox.getText().toString());}

    private void markConversationReadLocally(String cid){
        if(cid==null||cid.isEmpty())return;

        try{
            for(JSONObject c:inbox){
                if(cid.equals(c.optString("id"))){
                    c.put("unread",0);
                    break;
                }
            }

            String raw=cache.get("inbox");

            if(raw!=null&&!raw.isEmpty()){
                JSONObject root=new JSONObject(raw);
                JSONArray arr=root.optJSONArray("conversations");

                if(arr!=null){
                    for(int i=0;i<arr.length();i++){
                        JSONObject c=arr.optJSONObject(i);

                        if(c!=null&&cid.equals(c.optString("id"))){
                            c.put("unread",0);
                            break;
                        }
                    }

                    cache.put("inbox",root.toString());
                }
            }

            filterInbox(
                searchBox==null
                    ? ""
                    : searchBox.getText().toString()
            );

        }catch(Exception ignored){}
    }
    private void filterInbox(String q){filteredInbox.clear();String n=q==null?"":q.toLowerCase(Locale.ROOT).trim();for(JSONObject c:inbox){String name=c.optString("name").toLowerCase(Locale.ROOT),preview=c.optJSONObject("lastMessage")==null?"":c.optJSONObject("lastMessage").optString("body").toLowerCase(Locale.ROOT);if(n.isEmpty()||name.contains(n)||preview.contains(n))filteredInbox.add(c);}if(inboxAdapter!=null)inboxAdapter.notifyDataSetChanged();}

    private void updateInboxFromIncomingMessage(String cid,JSONObject m){
        if(cid==null||cid.isEmpty()||m==null)return;

        try{
            JSONObject conversation=null;
            int oldIndex=-1;

            for(int i=0;i<inbox.size();i++){
                JSONObject c=inbox.get(i);

                if(cid.equals(c.optString("id"))){
                    conversation=c;
                    oldIndex=i;
                    break;
                }
            }

            // Unknown/new conversation:
            // let refreshInbox() obtain its full metadata.
            if(conversation==null)return;

            JSONObject last=new JSONObject();

            last.put("id",m.optString("id"));
            last.put("senderId",m.optString("senderId"));
            last.put("body",m.optString("body"));
            last.put("type",m.optString("type","text"));
            last.put("createdAt",m.optString("createdAt"));
            last.put("status",m.optString("status","sent"));

            conversation.put("lastMessage",last);

            boolean chatOpen=
                activeConversation!=null &&
                cid.equals(activeConversation.optString("id"));

            boolean incomingFromOther=
                !m.optString("senderId").isEmpty() &&
                !m.optString("senderId").equals(selfId);

            if(chatOpen){
                conversation.put("unread",0);
            }else if(incomingFromOther){
                conversation.put(
                    "unread",
                    Math.max(0,conversation.optInt("unread"))+1
                );
            }

            // Move newest conversation to top immediately.
            if(oldIndex>0){
                inbox.remove(oldIndex);
                inbox.add(0,conversation);
            }

            // Persist the instantly updated inbox.
            String raw=cache.get("inbox");
            JSONObject root;

            if(raw==null||raw.isEmpty()){
                root=new JSONObject();
            }else{
                try{
                    root=new JSONObject(raw);
                }catch(Exception ignored){
                    root=new JSONObject();
                }
            }

            root.put("conversations",new JSONArray(inbox));
            cache.put("inbox",root.toString());

            filterInbox(
                searchBox==null
                    ? ""
                    : searchBox.getText().toString()
            );

        }catch(Exception ignored){}
    }

    private void openConversation(JSONObject c){activeConversation=c;replyTo=null;messages.clear();socketMessageBatch.clear();main.removeCallbacks(flushSocketMessages);socketMessageBatchConversationId="";composerHasText=false;typingStateSent=false;conversationInitialScrollPending=true;
        boolean instagramTheme="instagram".equals(activeTheme());
        if(instagramTheme){
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
        root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackground(themeConversationBackground());root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(6),0,dp(6),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(49)));ImageButton back=icon(R.drawable.ic_msg_back,35,instagramTheme?Color.WHITE:TEXT);head.addView(back);back.setOnClickListener(v->showInbox(false));View avatar=buildConversationAvatar(c,31);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(31),dp(31));ap.leftMargin=dp(1);head.addView(avatar,ap);LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);np.leftMargin=dp(7);head.addView(names,np);TextView name=text(c.optString("name","Conversation"),14,instagramTheme?Color.WHITE:TEXT,Typeface.BOLD);names.addView(name,new LinearLayout.LayoutParams(-1,dp(24)));TextView status=text(conversationStatus(c),10,instagramTheme?Color.rgb(142,142,142):SUB,Typeface.NORMAL);names.addView(status,new LinearLayout.LayoutParams(-1,dp(17)));ImageButton info=icon(R.drawable.ic_msg_info,35,instagramTheme?Color.WHITE:TEXT);head.addView(info);info.setOnClickListener(v->showInfo());View divider=new View(this);divider.setBackgroundColor(instagramTheme?Color.BLACK:DIV);page.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        FrameLayout messageArea=new FrameLayout(this);page.addView(messageArea,new LinearLayout.LayoutParams(-1,0,1));
        list=new ListView(this);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        list.setFriction(android.view.ViewConfiguration.getScrollFriction()*3f);
        list.setPadding(0,dp(12),0,dp(5));
        list.setClipToPadding(false);
        list.setBackground(themeConversationBackground());
        messageArea.addView(list,new FrameLayout.LayoutParams(-1,-1));

        // Older-message loader is a real ListView header row, never an overlay.
        FrameLayout loaderRow=new FrameLayout(this);
        loaderRow.setBackgroundColor(Color.TRANSPARENT);
        olderMessagesLoaderRow=loaderRow;

        olderMessagesSpinner=new OlderMessagesSpinner(this);
        olderMessagesSpinner.setVisibility(View.INVISIBLE);

        FrameLayout.LayoutParams spinnerLp=
            new FrameLayout.LayoutParams(
                dp(38),
                dp(38),
                Gravity.CENTER
            );
        loaderRow.addView(olderMessagesSpinner,spinnerLp);

        android.widget.AbsListView.LayoutParams loaderRowLp=
            new android.widget.AbsListView.LayoutParams(-1,dp(46));
        loaderRow.setLayoutParams(loaderRowLp);
        list.addHeaderView(loaderRow,null,false);

        if("group".equals(c.optString("type"))){
            list.addHeaderView(buildGroupConversationIntro(c),null,false);
        }

        messageAdapter=new MessageAdapter();
        list.setAdapter(messageAdapter);
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener(){
            private int state=SCROLL_STATE_IDLE;

            @Override public void onScrollStateChanged(
                android.widget.AbsListView view,
                int newState
            ){
                state=newState;
                if(newState!=SCROLL_STATE_IDLE && timeRevealOffset>0f){
                    timeRevealOffset=0f;
                    timeRevealDragging=false;
                    applyConversationTimeReveal();
                }

                if(
                    newState==SCROLL_STATE_IDLE &&
                    view.getFirstVisiblePosition()<=list.getHeaderViewsCount()
                ){
                    loadOlderMessagesPage();
                }
            }

            @Override public void onScroll(
                android.widget.AbsListView view,
                int firstVisibleItem,
                int visibleItemCount,
                int totalItemCount
            ){
                prefetchConversationWindow(firstVisibleItem,visibleItemCount);
            }
        });
        wireConversationTimeRevealGesture();
        typingView=text("",13,Color.rgb(138,141,145),Typeface.NORMAL);typingView.setPadding(dp(14),0,dp(8),0);typingView.setBackgroundColor(Color.TRANSPARENT);typingView.setVisibility(View.GONE);FrameLayout.LayoutParams tvp=new FrameLayout.LayoutParams(-2,dp(21),Gravity.START|Gravity.BOTTOM);tvp.leftMargin=dp(4);tvp.bottomMargin=dp(2);messageArea.addView(typingView,tvp);
        replyBar=new LinearLayout(this);replyBar.setGravity(Gravity.CENTER_VERTICAL);replyBar.setPadding(dp(15),dp(2),dp(8),dp(2));replyBar.setBackgroundColor(Color.WHITE);replyBar.setVisibility(View.GONE);page.addView(replyBar,new LinearLayout.LayoutParams(-1,dp(52)));
        buildComposer(page);loadCachedMessages(c.optString("id"));refreshMessages(c.optString("id"));markRead();}

    private View buildGroupConversationIntro(JSONObject c){LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);intro.setGravity(Gravity.CENTER_HORIZONTAL);intro.setPadding(dp(12),dp(26),dp(12),dp(26));View avatar=buildConversationAvatar(c,92);intro.addView(avatar,new LinearLayout.LayoutParams(dp(92),dp(92)));TextView title=text(c.optString("name","Group chat"),20,TEXT,Typeface.BOLD);title.setGravity(Gravity.CENTER);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(42));tp.topMargin=dp(12);intro.addView(title,tp);TextView change=text("Change name and image",14,BLUE,Typeface.BOLD);change.setGravity(Gravity.CENTER);intro.addView(change,new LinearLayout.LayoutParams(-1,dp(35)));change.setOnClickListener(v->beginGroupEditor(c));if(c.optBoolean("isOwner")){TextView owner=text("You created this group",12,Color.rgb(138,141,145),Typeface.NORMAL);owner.setGravity(Gravity.CENTER);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(36));op.topMargin=dp(6);intro.addView(owner,op);}String who=c.optBoolean("namedByIsSelf")?"You":c.optString("namedByName","Someone");TextView named=text(who+" named the group “"+c.optString("name","Group chat")+"”.",12,Color.rgb(138,141,145),Typeface.NORMAL);named.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(44));np.topMargin=dp(4);intro.addView(named,np);return intro;}
    private void beginGroupEditor(JSONObject c){groupEditConversation=c;groupEditImageData="";groupEditEmoji="";groupEditColor="#3da9ef";groupEditDraftName=c==null?"":c.optString("name");showGroupEditor(c);}
    private void showGroupEditor(JSONObject c){
        if(c==null||!"group".equals(c.optString("type")))return;groupEditConversation=c;
        root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(11,15,20));root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(13),0,dp(13),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(50)));Button cancel=new Button(this);cancel.setAllCaps(false);cancel.setText("Cancel");cancel.setTextColor(Color.WHITE);cancel.setTextSize(15);cancel.setBackgroundColor(Color.TRANSPARENT);head.addView(cancel,new LinearLayout.LayoutParams(dp(82),dp(44)));Space hs=new Space(this);head.addView(hs,new LinearLayout.LayoutParams(0,dp(44),1));Button done=new Button(this);done.setAllCaps(false);done.setText("Done");done.setTextColor(BLUE);done.setTextSize(15);done.setTypeface(Typeface.DEFAULT,Typeface.BOLD);done.setBackgroundColor(Color.TRANSPARENT);head.addView(done,new LinearLayout.LayoutParams(dp(72),dp(44)));cancel.setOnClickListener(v->showInfo());
        ScrollView sc=new ScrollView(this);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(17),dp(2),dp(17),dp(26));sc.addView(body,new ScrollView.LayoutParams(-1,-2));
        FrameLayout preview=new FrameLayout(this);body.addView(preview,new LinearLayout.LayoutParams(-1,dp(150)));renderGroupEditPreview(preview,c);
        LinearLayout nameBox=new LinearLayout(this);nameBox.setOrientation(LinearLayout.VERTICAL);nameBox.setPadding(dp(14),dp(10),dp(14),dp(8));GradientDrawable nb=bg(Color.TRANSPARENT,12);nb.setStroke(dp(1),Color.rgb(52,57,65));nameBox.setBackground(nb);LinearLayout.LayoutParams nbp=new LinearLayout.LayoutParams(-1,dp(76));nbp.bottomMargin=dp(19);body.addView(nameBox,nbp);LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);nameBox.addView(meta,new LinearLayout.LayoutParams(-1,dp(18)));TextView label=text("Group name",12,Color.rgb(174,179,187),Typeface.NORMAL);meta.addView(label,new LinearLayout.LayoutParams(0,-1,1));TextView count=text(Math.min(100,c.optString("name").length())+"/100",11,Color.rgb(174,179,187),Typeface.NORMAL);count.setGravity(Gravity.END);meta.addView(count,new LinearLayout.LayoutParams(dp(55),-1));EditText name=new EditText(this);name.setSingleLine(true);name.setText(groupEditDraftName.isEmpty()?c.optString("name"):groupEditDraftName);name.setTextColor(Color.WHITE);name.setTextSize(18);name.setBackgroundColor(Color.TRANSPARENT);name.setPadding(0,0,0,0);nameBox.addView(name,new LinearLayout.LayoutParams(-1,dp(38)));name.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int b,int d){}public void onTextChanged(CharSequence x,int a,int b,int d){if(x.length()>100){name.setText(x.subSequence(0,100));name.setSelection(name.length());return;}count.setText(name.length()+"/100");groupEditDraftName=name.getText().toString();done.setEnabled(!name.getText().toString().trim().isEmpty());}public void afterTextChanged(Editable e){}});
        GridLayout grid=new GridLayout(this);grid.setColumnCount(4);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);grid.setColumnOrderPreserved(false);body.addView(grid,new LinearLayout.LayoutParams(-1,-2));String[] presets={"📚","🔥","🎉","💎","🏈","🌮","😎","💖","✨","👀","🌈","🦄","🌻","🎂","👗","✌️","🎄","🌶️","🏠","📣","💅","☁️"};int[] colors={0xffff9f43,0xffff2f8e,0xffdf36df,0xffff485d,0xff925bea,0xff3da9ef,0xff54d991,0xffffd35a};
        java.util.function.BiConsumer<View,Integer> addChoice=(child,indexChoice)->{FrameLayout cell=new FrameLayout(this);GridLayout.LayoutParams cellLp=new GridLayout.LayoutParams();cellLp.width=0;cellLp.height=dp(68);cellLp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);cellLp.setMargins(dp(4),dp(5),dp(4),dp(5));grid.addView(cell,cellLp);FrameLayout.LayoutParams childLp=new FrameLayout.LayoutParams(dp(50),dp(50),Gravity.CENTER);cell.addView(child,childLp);};
        ImageButton more=icon(R.drawable.ic_msg_plus,50,Color.WHITE);more.setBackground(bg(Color.rgb(40,45,52),25));addChoice.accept(more,0);more.setOnClickListener(v->showGroupEmojiPicker(c));
        ImageButton gallery=icon(R.drawable.ic_info_gallery,50,Color.WHITE);gallery.setBackground(bg(Color.rgb(40,45,52),25));addChoice.accept(gallery,1);gallery.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,PICK_GROUP_IMAGE);});
        for(int i=0;i<presets.length;i++){TextView e=text(presets[i],24,Color.WHITE,Typeface.NORMAL);e.setGravity(Gravity.CENTER);e.setBackground(bg(colors[i%colors.length],25));addChoice.accept(e,i+2);final String em=presets[i];final int col=colors[i%colors.length];e.setOnClickListener(v->{groupEditEmoji=em;groupEditColor=String.format(Locale.US,"#%06x",0xffffff&col);groupEditImageData="";renderGroupEditPreview(preview,c);});}
        done.setOnClickListener(v->saveGroupEdit(c,name.getText().toString().trim()));
    }
    private void renderGroupEditPreview(FrameLayout preview,JSONObject c){preview.removeAllViews();View avatar;if(!groupEditImageData.isEmpty()){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);try{String b64=groupEditImageData.substring(groupEditImageData.indexOf(',')+1);byte[] raw=Base64.decode(b64,Base64.DEFAULT);im.setImageBitmap(BitmapFactory.decodeByteArray(raw,0,raw.length));}catch(Exception ignored){}avatar=im;}else if(!groupEditEmoji.isEmpty()){TextView e=text(groupEditEmoji,43,Color.WHITE,Typeface.NORMAL);e.setGravity(Gravity.CENTER);try{e.setBackground(bg(Color.parseColor(groupEditColor),46));}catch(Exception ex){e.setBackground(bg(BLUE,46));}avatar=e;}else avatar=buildConversationAvatar(c,92);FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(92),dp(92),Gravity.CENTER);preview.addView(avatar,ap);}
    private String emojiDataUrl(String emoji,String color){
        try{
            final int size=240;
            Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);
            Paint bgPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            int bgColor=BLUE;
            try{if(color!=null&&!color.isEmpty())bgColor=Color.parseColor(color);}catch(Exception ignored){}
            bgPaint.setColor(bgColor);
            canvas.drawCircle(size/2f,size/2f,size/2f,bgPaint);

            Paint emojiPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
            emojiPaint.setTextAlign(Paint.Align.CENTER);
            emojiPaint.setTextSize(112f);
            emojiPaint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.NORMAL));
            Paint.FontMetrics fm=emojiPaint.getFontMetrics();
            float baseline=size/2f-(fm.ascent+fm.descent)/2f;
            canvas.drawText(emoji==null?"":emoji,size/2f,baseline,emojiPaint);

            ByteArrayOutputStream out=new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
            bitmap.recycle();
            return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        }catch(Exception e){
            return "";
        }
    }

    private void saveGroupEdit(JSONObject c,String title){if(title.isEmpty())return;String image=groupEditImageData;if(image.isEmpty()&&!groupEditEmoji.isEmpty())image=emojiDataUrl(groupEditEmoji,groupEditColor);try{api.patch("/api/messaging/conversations/"+c.optString("id")+"/group",new JSONObject().put("title",title).put("image",image),(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject nc=json.optJSONObject("conversation");if(nc!=null)activeConversation=nc;openConversation(nc==null?c:nc);}));}catch(Exception e){toast(e.getMessage());}}

    private void showGroupEmojiPicker(JSONObject c){
        root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(11,15,20));root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(13),0,dp(13),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(61)));
        Button cancel=new Button(this);cancel.setText("Cancel");cancel.setAllCaps(false);cancel.setTextColor(Color.WHITE);cancel.setTextSize(17);cancel.setBackgroundColor(Color.TRANSPARENT);head.addView(cancel,new LinearLayout.LayoutParams(dp(82),dp(48)));
        Space hs=new Space(this);head.addView(hs,new LinearLayout.LayoutParams(0,dp(48),1));Button done=new Button(this);done.setText("Done");done.setAllCaps(false);done.setTextColor(Color.rgb(22,136,255));done.setTextSize(17);done.setBackgroundColor(Color.TRANSPARENT);head.addView(done,new LinearLayout.LayoutParams(dp(72),dp(48)));
        final String[] picked={groupEditEmoji.isEmpty()?"😎":groupEditEmoji};final String[] color={groupEditColor==null||groupEditColor.isEmpty()?"#3da9ef":groupEditColor};
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(17),dp(4),dp(17),dp(26));page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        TextView preview=text(picked[0],58,Color.WHITE,Typeface.NORMAL);preview.setGravity(Gravity.CENTER);preview.setBackground(bg(Color.parseColor(color[0]),59));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(118),dp(118));pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=dp(36);pp.bottomMargin=dp(25);body.addView(preview,pp);
        HorizontalScrollView colorScroll=new HorizontalScrollView(this);colorScroll.setHorizontalScrollBarEnabled(false);LinearLayout colorRow=new LinearLayout(this);colorRow.setGravity(Gravity.CENTER_VERTICAL);colorScroll.addView(colorRow,new HorizontalScrollView.LayoutParams(-2,dp(54)));body.addView(colorScroll,new LinearLayout.LayoutParams(-1,dp(58)));
        String[] colors={"#ff9f43","#ff2f8e","#df36df","#ff485d","#925bea","#3da9ef","#54d991","#ffd35a"};for(String col:colors){FrameLayout holder=new FrameLayout(this);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(42),dp(42));hp.rightMargin=dp(10);colorRow.addView(holder,hp);View dot=new View(this);dot.setBackground(bg(Color.parseColor(col),22));FrameLayout.LayoutParams dpv=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER);holder.addView(dot,dpv);holder.setOnClickListener(v->{color[0]=col;preview.setBackground(bg(Color.parseColor(col),59));groupEditColor=col;});}
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Search");search.setHintTextColor(Color.rgb(174,179,187));search.setTextColor(Color.WHITE);search.setTextSize(17);search.setPadding(dp(13),0,dp(13),0);search.setBackground(bg(Color.rgb(41,46,53),9));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(45));sp.bottomMargin=dp(15);body.addView(search,sp);
        ScrollView sc=new ScrollView(this);GridLayout grid=new GridLayout(this);grid.setColumnCount(6);sc.addView(grid,new ScrollView.LayoutParams(-1,-2));body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        String emojiText="😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫣 🤭 🫢 🫡 🤫 🫠 🤥 😶 🫥 😐 🫤 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 😵‍💫 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 👀 👁️ 💋 🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐧 🦅 🦉 🦋 🐢 🐍 🐙 🐠 🐬 🦈 🍏 🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🍅 🥑 🥦 🍕 🍔 🍟 🌮 🍣 🍰 🎂 ☕ 🧋 ⚽ 🏀 🏈 ⚾ 🎾 🎮 🎨 🎬 🎤 🎧 🎹 🎸 🎯 🚗 🚕 🚌 🚓 🚑 🚒 🚲 ✈️ 🚀 🚁 ⛵ 🏖️ 🏝️ 🏠 🏢 📱 💻 📷 🎥 📞 📺 🎧 ⏰ 💡 💸 💎 🔧 🔨 🔮 💊 🔑 🎁 📦 📚 📎 📌 🔍 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❤️‍🔥 💕 💞 💯 ❗ ❓ ⚠️ ✅ 🌐 ▶️ ⏸️ ➡️ ⬅️ ⬆️ ⬇️ 🏳️ 🏴 🏁 🚩 🏳️‍🌈 🇵🇸 🇯🇴 🇪🇬 🇸🇦 🇦🇪 🇬🇧 🇺🇸 🇨🇦 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇯🇵 🇰🇷 🇮🇳 🇧🇷";
        List<TextView> buttons=new ArrayList<>();for(String em:emojiText.split(" ")){TextView b=text(em,29,Color.WHITE,Typeface.NORMAL);b.setGravity(Gravity.CENTER);GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(48);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);grid.addView(b,lp);buttons.add(b);b.setOnClickListener(v->{picked[0]=em;preview.setText(em);for(TextView x:buttons)x.setBackgroundColor(Color.TRANSPARENT);b.setBackground(bg(Color.rgb(41,46,53),10));});}
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int c1,int a1){}public void onTextChanged(CharSequence x,int st,int b,int c1){String q=x.toString().trim();for(TextView tv:buttons)tv.setVisibility(q.isEmpty()||tv.getText().toString().contains(q)?View.VISIBLE:View.GONE);}public void afterTextChanged(Editable e){}});
        cancel.setOnClickListener(v->showGroupEditor(c));done.setOnClickListener(v->{groupEditEmoji=picked[0];groupEditColor=color[0];groupEditImageData="";showGroupEditor(c);});
    }

    private void buildComposer(LinearLayout page){
        buildInstagramComposer(page);
    }

    private void buildInstagramComposer(LinearLayout page){
        composer=new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(7),dp(4),dp(7),dp(4));
        composer.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams outerLp=new LinearLayout.LayoutParams(-1,dp(58));
        outerLp.setMargins(dp(3),dp(2),dp(3),dp(4));
        page.addView(composer,outerLp);

        LinearLayout pill=new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(5),0,dp(4),0);
        pill.setBackground(bg(Color.rgb(38,38,38),28));
        composer.addView(pill,new LinearLayout.LayoutParams(-1,dp(52)));

        ImageButton camera=icon(R.drawable.ic_instagram_camera,42,Color.WHITE);
        camera.clearColorFilter();
        camera.setPadding(0,0,0,0);
        camera.setBackgroundColor(Color.TRANSPARENT);
        camera.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pill.addView(camera,new LinearLayout.LayoutParams(dp(42),dp(42)));

        messageInput=new EditText(this);
        messageInput.setHint("Message...");
        messageInput.setHintTextColor(Color.rgb(142,142,142));
        messageInput.setTextColor(Color.WHITE);
        messageInput.setTextSize(15);
        messageInput.setSingleLine(false);
        messageInput.setMaxLines(4);
        messageInput.setPadding(dp(10),dp(4),dp(4),dp(4));
        messageInput.setBackgroundColor(Color.TRANSPARENT);
        messageInput.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                closePickersOnly();
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                closePickersAndOpenKeyboard();
            }
            return false;
        });
        messageInput.setOnClickListener(v->closePickersAndOpenKeyboard());
        pill.addView(messageInput,new LinearLayout.LayoutParams(0,dp(42),1));
        composer.setClickable(true);
        composer.setOnClickListener(v->closePickersAndOpenKeyboard());
        pill.setClickable(true);
        pill.setOnClickListener(v->closePickersAndOpenKeyboard());

        ImageButton mic=icon(R.drawable.ic_instagram_mic,30,Color.WHITE);
        ImageButton gallery=icon(R.drawable.ic_instagram_gallery,30,Color.WHITE);
        ImageButton sticker=icon(R.drawable.ic_instagram_sticker,30,Color.WHITE);
        ImageButton plus=icon(R.drawable.ic_instagram_plus,30,Color.WHITE);
        for(ImageButton b:new ImageButton[]{mic,gallery,sticker,plus}){
            b.clearColorFilter();
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setScaleType(ImageView.ScaleType.FIT_CENTER);
            b.setPadding(dp(4),dp(4),dp(4),dp(4));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(30),dp(30));
            bp.leftMargin=dp(4);
            bp.rightMargin=dp(4);
            pill.addView(b,bp);
        }

        sendButton=icon(R.drawable.msg_send_enabled,44,Color.WHITE);
        sendButton.setImageResource(R.drawable.msg_send_enabled);
        sendButton.setColorFilter(Color.WHITE);
        sendButton.setBackground(bg(Color.rgb(98,55,255),22));
        sendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendButton.setPadding(dp(8),dp(8),dp(8),dp(8));
        sendButton.setVisibility(View.GONE);
        pill.addView(sendButton,new LinearLayout.LayoutParams(dp(44),dp(34)));
        micButton=mic;

        camera.setOnClickListener(v->openMessageCamera());
        gallery.setOnClickListener(v->{
            forceFullScreenMediaPicker=false;
            mediaPickerPreviewOrigin=0;
            pickInstagramMedia();
        });
        sticker.setOnClickListener(v->showMessengerStickerPicker());
        plus.setOnClickListener(v->pickAttachment());
        sendButton.setOnClickListener(v->sendText());
        mic.setOnClickListener(v->{});
        mic.setOnTouchListener((v,e)->{
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return handleMicTouch(e);
        });

        messageInput.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence cs,int st,int c,int a){}
            public void onTextChanged(CharSequence cs,int st,int b,int c){}
            public void afterTextChanged(Editable e){
                boolean has=e!=null&&e.length()>0;
                composerHasText=has;
                sendButton.setVisibility(has?View.VISIBLE:View.GONE);
                mic.setVisibility(has?View.GONE:View.VISIBLE);
                gallery.setVisibility(has?View.GONE:View.VISIBLE);
                sticker.setVisibility(has?View.GONE:View.VISIBLE);
                plus.setVisibility(has?View.GONE:View.VISIBLE);
                queueTypingPulse();
            }
        });
        composerHasText=false;
    }

    private void syncComposerAction(){boolean has=messageInput!=null&&composerHasText;if(sendButton!=null){sendButton.setVisibility(has?View.VISIBLE:View.GONE);sendButton.setImageResource(R.drawable.msg_send_enabled);sendButton.setColorFilter(Color.WHITE);sendButton.setBackground(bg(Color.rgb(98,55,255),19));}if(micButton!=null){micButton.setColorFilter(themeAccent());micButton.setVisibility(has?View.GONE:View.VISIBLE);}}

    private void loadCachedMessages(String cid){String raw=cache.get("messages:"+cid);if(raw==null)return;try{applyMessages(new JSONObject(raw).optJSONArray("messages"),false);}catch(Exception ignored){}}
    private void refreshMessages(String cid){if(refreshingMessages)return;refreshingMessages=true;api.get("/api/messaging/conversations/"+cid+"/messages?limit=80",(json,error)->main.post(()->{refreshingMessages=false;if(error!=null)return;beforeCursor=json.optString("nextBefore","");cache.put("messages:"+cid,json.toString());applyMessages(json.optJSONArray("messages"),true);if(activeConversation!=null&&activeConversation.optString("id").equals(cid))markRead();}));}
    private String firstAttachmentName(JSONObject message){JSONArray a=message==null?null:message.optJSONArray("attachments");JSONObject item=a!=null&&a.length()>0?a.optJSONObject(0):null;return item==null?"":item.optString("name","").replaceFirst("^__vm[12]__","");}
    private JSONObject findPreviousMessage(List<JSONObject> previous,JSONObject incoming){if(incoming==null)return null;String id=incoming.optString("id"),client=incoming.optString("clientId");for(JSONObject old:previous){if(!id.isEmpty()&&id.equals(old.optString("id")))return old;if(!client.isEmpty()&&client.equals(old.optString("clientId")))return old;}if(isMine(incoming)){String incomingName=firstAttachmentName(incoming);for(int i=previous.size()-1;i>=0;i--){JSONObject old=previous.get(i);if(!old.optBoolean("pending")||!isMine(old))continue;String oldName=firstAttachmentName(old);if(!incomingName.isEmpty()&&incomingName.equals(oldName))return old;}JSONObject only=null;int candidates=0;String incomingType=incoming.optString("type");for(int i=previous.size()-1;i>=0;i--){JSONObject old=previous.get(i);if(old.optBoolean("pending")&&isMine(old)&&incomingType.equals(old.optString("type"))){only=old;candidates++;}}if(candidates==1)return only;}return null;}
    private void preserveLocalMediaPreview(JSONObject oldMessage,JSONObject merged){if(oldMessage==null||merged==null)return;JSONArray oldAttachments=oldMessage.optJSONArray("attachments"),newAttachments=merged.optJSONArray("attachments");JSONObject oldAttachment=oldAttachments!=null&&oldAttachments.length()>0?oldAttachments.optJSONObject(0):null,newAttachment=newAttachments!=null&&newAttachments.length()>0?newAttachments.optJSONObject(0):null;if(oldAttachment==null||newAttachment==null)return;String local=oldAttachment.optString("localPreviewUrl",oldAttachment.optString("url",""));if(local.startsWith("file:"))try{newAttachment.put("localPreviewUrl",local);if(newAttachment.optInt("width",0)<=0&&oldAttachment.optInt("width",0)>0)newAttachment.put("width",oldAttachment.optInt("width"));if(newAttachment.optInt("height",0)<=0&&oldAttachment.optInt("height",0)>0)newAttachment.put("height",oldAttachment.optInt("height"));if(newAttachment.optString("mime","").isEmpty())newAttachment.put("mime",oldAttachment.optString("mime"));if(newAttachment.optString("name","").isEmpty())newAttachment.put("name",oldAttachment.optString("name"));}catch(Exception ignored){}}
    private String temporaryVideoLocalUrl(String url){if(url==null||url.isEmpty())return"";String remembered=temporaryVideoFiles.get(url);if(remembered!=null){String path=Uri.parse(remembered).getPath();if(path!=null&&new File(path).exists())return remembered;}File cached=new File(getCacheDir(),"temporary-view-"+Integer.toHexString(url.hashCode())+".video");if(cached.exists()&&cached.length()>0){String local=Uri.fromFile(cached).toString();temporaryVideoFiles.put(url,local);return local;}return"";}
    private void preloadTemporaryVideo(JSONObject attachment,Runnable ready){if(attachment==null)return;String url=attachment.optString("url","");if(url.isEmpty())return;String local=temporaryVideoLocalUrl(url);if(!local.isEmpty()){try{attachment.put("localPreloadedUrl",local);}catch(Exception ignored){}if(ready!=null)ready.run();return;}if(ready!=null)temporaryVideoWaiters.computeIfAbsent(url,k->new ArrayList<>()).add(ready);if(temporaryVideoLoading.contains(url))return;temporaryVideoLoading.add(url);temporaryMediaExecutor.execute(()->{String result="";try{byte[] bytes=api.getBytesSync(url);if(bytes!=null&&bytes.length>0){File cached=new File(getCacheDir(),"temporary-view-"+Integer.toHexString(url.hashCode())+".video");try(FileOutputStream out=new FileOutputStream(cached)){out.write(bytes);}result=Uri.fromFile(cached).toString();}}catch(Exception ignored){}String loaded=result;main.post(()->{temporaryVideoLoading.remove(url);List<Runnable> waiters=temporaryVideoWaiters.remove(url);if(!loaded.isEmpty()){temporaryVideoFiles.put(url,loaded);try{attachment.put("localPreloadedUrl",loaded);}catch(Exception ignored){}if(waiters!=null)for(Runnable callback:waiters)callback.run();}});});}
    private void prefetchTemporaryMedia(){if(images==null)return;for(JSONObject message:messages){if(isMine(message))continue;JSONArray attachments=message.optJSONArray("attachments");JSONObject attachment=attachments!=null&&attachments.length()>0?attachments.optJSONObject(0):null;if(attachment==null||mediaViewMode(message,attachment)<=0)continue;String type=actualMediaType(message.optString("type"),attachment);if("image".equals(type))images.prefetch(attachment.optString("url"));else if("video".equals(type))preloadTemporaryVideo(attachment,null);}}
    private void prefetchConversationWindow(int firstVisibleItem,int visibleItemCount){if(messageAdapter==null||messages.isEmpty())return;int headers=list==null?0:list.getHeaderViewsCount();int firstRow=Math.max(0,firstVisibleItem-headers-2),lastRow=Math.min(messageAdapter.getCount()-1,firstVisibleItem-headers+visibleItemCount+2);for(int row=firstRow;row<=lastRow;row++){int start=messageAdapter.messageIndexForRow(row),end=messageAdapter.messageEndForRow(row);for(int i=start;i<=end&&i>=0&&i<messages.size();i++)prefetchMessageThumbnail(messages.get(i));}}
    private void prefetchConversationTail(){int start=Math.max(0,messages.size()-8);for(int i=messages.size()-1;i>=start;i--)prefetchMessageThumbnail(messages.get(i));}
    private void prefetchMessageThumbnail(JSONObject message){if(message==null)return;JSONArray attachments=message.optJSONArray("attachments");JSONObject attachment=attachments!=null&&attachments.length()>0?attachments.optJSONObject(0):null;if(attachment==null||mediaViewMode(message,attachment)>0)return;String type=actualMediaType(message.optString("type"),attachment),url=displayMediaUrl(attachment);if("image".equals(type)){if(isStickerMessage(message))stickers.prefetch(url);else{int width=Math.min(dp(180),(int)(getResources().getDisplayMetrics().widthPixels*.52f));images.prefetch(url,width,stableMediaHeight(attachment,width,false));}}}
    private void applyMessages(JSONArray arr,boolean fromNetwork){if(arr==null)return;boolean keepBottom=conversationInitialScrollPending||isConversationAtBottom();List<JSONObject> previous=new ArrayList<>(messages),next=new ArrayList<>();Set<String> matched=new HashSet<>();for(int i=0;i<arr.length();i++){JSONObject incoming=arr.optJSONObject(i);if(incoming==null||incoming.optBoolean("deleted"))continue;JSONObject old=findPreviousMessage(previous,incoming),merged=old==null?incoming:mergeMessage(old,incoming);preserveLocalMediaPreview(old,merged);next.add(merged);if(old!=null)matched.add(old.optString("id")+"|"+old.optString("clientId"));}if(fromNetwork){long now=System.currentTimeMillis();for(JSONObject old:previous){String key=old.optString("id")+"|"+old.optString("clientId");if(matched.contains(key)||old.optBoolean("deleted"))continue;if(old.optBoolean("pending")||old.optLong("optimisticRetainUntil",0)>now)next.add(old);}}messages.clear();messages.addAll(next);prefetchTemporaryMedia();prefetchConversationTail();if(messageAdapter!=null){messageAdapter.notifyDataSetChanged();if(keepBottom)scrollToAbsoluteBottom();}conversationInitialScrollPending=false;}
    private void cacheMessagesNow(){if(activeConversation==null)return;try{cache.put("messages:"+activeConversation.optString("id"),new JSONObject().put("messages",new JSONArray(messages)).toString());}catch(Exception ignored){}}

    private void cacheIncomingMessage(String cid,JSONObject incoming,boolean allowAppend){
        if(cid==null||cid.isEmpty()||incoming==null)return;
        try{
            String key="messages:"+cid;
            String raw=cache.get(key);

            JSONObject root;
            if(raw==null||raw.isEmpty())root=new JSONObject();
            else root=new JSONObject(raw);

            JSONArray current=root.optJSONArray("messages");
            if(current==null)current=new JSONArray();

            JSONArray updated=new JSONArray();

            String incomingId=incoming.optString("id");
            String incomingClient=incoming.optString("clientId");
            boolean found=false;

            for(int i=0;i<current.length();i++){
                JSONObject old=current.optJSONObject(i);
                if(old==null)continue;

                boolean sameId=
                    !incomingId.isEmpty() &&
                    incomingId.equals(old.optString("id"));

                boolean sameClient=
                    !incomingClient.isEmpty() &&
                    incomingClient.equals(old.optString("clientId"));

                if(sameId||sameClient){
                    updated.put(mergeMessage(old,incoming));
                    found=true;
                }else{
                    updated.put(old);
                }
            }

            if(!found&&allowAppend&&!incoming.optBoolean("deleted")){
                updated.put(incoming);
            }

            // Keep the same latest-message cache size used by chat loading.
            if(updated.length()>80){
                JSONArray trimmed=new JSONArray();
                int start=updated.length()-80;

                for(int i=start;i<updated.length();i++){
                    JSONObject item=updated.optJSONObject(i);
                    if(item!=null)trimmed.put(item);
                }

                updated=trimmed;
            }

            root.put("messages",updated);
            cache.put(key,root.toString());

        }catch(Exception ignored){}
    }

    private boolean isKeyboardVisible(){try{Rect r=new Rect();root.getWindowVisibleDisplayFrame(r);return root.getRootView().getHeight()-r.bottom>dp(120);}catch(Exception e){return false;}}
    private void keepKeyboardStateAfterSend(boolean wasVisible){if(messageInput==null)return;if(wasVisible){messageInput.requestFocus();}else{messageInput.clearFocus();InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(imm!=null)imm.hideSoftInputFromWindow(messageInput.getWindowToken(),0);}}

    private boolean isConversationAtBottom(){if(list==null||list.getCount()==0)return true;return list.getLastVisiblePosition()>=list.getCount()-2;}
    private void scheduleMessageRefresh(boolean keepBottom){scheduledMessageRefreshKeepsBottom|=keepBottom;main.removeCallbacks(messageRefresh);main.postDelayed(messageRefresh,90);}
    private void scrollToAbsoluteBottom(){
        if(list==null||messageAdapter==null||messageAdapter.getCount()==0)return;
        main.removeCallbacks(bottomAnchor);
        main.postDelayed(bottomAnchor,24);
    }

    private boolean isPendingClient(String clientId){
        for(JSONObject m:messages)if(clientId.equals(m.optString("clientId")))return m.optBoolean("pending");
        return false;
    }

    private void sendPendingTextRequest(String cid,String client,String body,JSONObject reply,int attempt){
        if(!isPendingClient(client))return;
        try{
            JSONObject req=new JSONObject().put("body",body).put("clientId",client);
            if(reply!=null)req.put("replyToId",reply.optString("id"));
            api.post("/api/messaging/conversations/"+cid+"/messages",req,(json,error)->main.post(()->{
                if(error!=null){
                    if(isPendingClient(client)){
                        long delay=Math.min(12000L,1800L+(long)Math.min(attempt,12)*850L);
                        main.postDelayed(()->sendPendingTextRequest(cid,client,body,reply,attempt+1),delay);
                        if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();
                    }
                    return;
                }
                JSONObject confirmed=json.optJSONObject("message");
                if(confirmed!=null){
                    if(reply!=null&&confirmed.optJSONObject("reply")==null){
                        try{confirmed.put("reply",new JSONObject().put("id",reply.optString("id")).put("body",reply.optString("body")).put("type",reply.optString("type","text")).put("senderName",senderName(reply)));}catch(Exception ignored){}
                    }
                    replaceOptimistic(client,confirmed);
                    cacheMessagesNow();
                    refreshInbox();
                }
            }));
        }catch(Exception error){
            if(isPendingClient(client))main.postDelayed(()->sendPendingTextRequest(cid,client,body,reply,attempt+1),Math.min(12000L,2200L+(long)Math.min(attempt,12)*850L));
        }
    }

    private void sendText(){
        if(activeConversation==null||messageInput==null)return;
        stickerLastConversations.remove(activeConversation.optString("id"));
        String body=messageInput.getText().toString().trim();
        if(body.isEmpty())return;
        boolean keyboardWasVisible=isKeyboardVisible();
        String cid=activeConversation.optString("id"),client=UUID.randomUUID().toString();
        JSONObject reply=replyTo;
        JSONObject temp=buildOptimisticText(body,client,reply);
        messages.add(temp);
        if(messageAdapter!=null){messageAdapter.notifyDataSetChanged();scrollToAbsoluteBottom();}
        cacheMessagesNow();
        messageInput.setText("");
        setReply(null);
        keepKeyboardStateAfterSend(keyboardWasVisible);
        sendPendingTextRequest(cid,client,body,reply,0);
    }
    private boolean isVisualMediaMessage(JSONObject message){if(message==null)return false;JSONArray attachments=message.optJSONArray("attachments");JSONObject attachment=attachments!=null&&attachments.length()>0?attachments.optJSONObject(0):null;String type=actualMediaType(message.optString("type"),attachment);return"image".equals(type)||"video".equals(type)||message.optBoolean("sticker");}
    private void hidePendingIndicator(String clientId){if(list==null||clientId==null||clientId.isEmpty())return;View pending=list.findViewWithTag("pending-send-"+clientId);if(pending!=null)pending.setVisibility(View.GONE);}
    private JSONObject buildOptimisticText(String body,String client,JSONObject reply){JSONObject temp=new JSONObject();try{temp.put("id","tmp-"+System.nanoTime());temp.put("clientId",client);temp.put("conversationId",activeConversation==null?"":activeConversation.optString("id"));temp.put("senderId",selfId);temp.put("type","text");temp.put("body",body);temp.put("createdAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date()));temp.put("status","sending");temp.put("pending",true);temp.put("sender",new JSONObject().put("id",selfId).put("name","You").put("isSelf",true));temp.put("attachments",new JSONArray());temp.put("reactions",new JSONArray());if(reply!=null)temp.put("reply",new JSONObject().put("id",reply.optString("id")).put("body",reply.optString("body")).put("type",reply.optString("type","text")).put("senderName",senderName(reply)));}catch(Exception ignored){}return temp;}
    private void replaceOptimistic(String clientId,JSONObject server){boolean keepBottom=isConversationAtBottom();for(int i=0;i<messages.size();i++){JSONObject x=messages.get(i);if(clientId.equals(x.optString("clientId"))){boolean visual=isVisualMediaMessage(x);JSONObject merged=mergeMessage(x,server);preserveLocalMediaPreview(x,merged);messages.set(i,merged);try{messages.get(i).put("pending",false).put("optimisticRetainUntil",System.currentTimeMillis()+30000L);}catch(Exception ignored){}if(visual)hidePendingIndicator(clientId);else scheduleMessageRefresh(keepBottom);return;}}upsertMessage(server);}
    private void markOptimisticFailed(String clientId){for(JSONObject x:messages)if(clientId.equals(x.optString("clientId")))try{x.put("pending",false).put("failed",true).put("status","failed");}catch(Exception ignored){}if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();}
    private void upsertMessage(JSONObject m){boolean keepBottom=isConversationAtBottom(),visualConfirmation=false;JSONObject old=findPreviousMessage(messages,m);if(old!=null){visualConfirmation=old.optBoolean("pending")&&isVisualMediaMessage(old);int index=messages.indexOf(old);JSONObject merged=mergeMessage(old,m);preserveLocalMediaPreview(old,merged);messages.set(index,merged);try{messages.get(index).put("pending",false).put("optimisticRetainUntil",System.currentTimeMillis()+30000L);}catch(Exception ignored){}if(visualConfirmation)hidePendingIndicator(old.optString("clientId"));}else messages.add(m);prefetchTemporaryMedia();if(!visualConfirmation)scheduleMessageRefresh(keepBottom);}
    private long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private void vibrateSafe(long ms){try{Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);if(v==null||!v.hasVibrator())return;long duration=Math.max(42,ms);if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(duration,225));else v.vibrate(duration);}catch(Exception ignored){}}

    private void setReply(JSONObject m){replyTo=m;if(replyBar==null)return;replyBar.removeAllViews();
        if(m==null){
            replyBar.animate().cancel();
            replyBar.setAlpha(1f);
            replyBar.setTranslationY(0);
            replyBar.setVisibility(View.GONE);
            return;
        }

        replyBar.setVisibility(View.VISIBLE);
        replyBar.animate().cancel();
        replyBar.setAlpha(0f);
        replyBar.setTranslationY(dp(10));
        replyBar.animate()
            .alpha(1f)
            .translationY(0)
            .setDuration(185)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.TOP);copy.setPadding(0,0,0,0);replyBar.addView(copy,new LinearLayout.LayoutParams(0,-1,1));JSONObject rs=m.optJSONObject("sender");TextView small=text("Replying to "+(rs==null?"message":rs.optString("name","message")),11.5f,Color.rgb(138,141,145),Typeface.NORMAL);small.setSingleLine(true);copy.addView(small,new LinearLayout.LayoutParams(-1,dp(17)));TextView pv=text(replyPreview(m),13.5f,TEXT,Typeface.NORMAL);pv.setSingleLine(true);pv.setEllipsize(TextUtils.TruncateAt.END);copy.addView(pv,new LinearLayout.LayoutParams(-1,dp(22)));ImageButton x=icon(R.drawable.ic_msg_close,30,Color.rgb(138,141,145));replyBar.addView(x);x.setOnClickListener(v->setReply(null));}
    private String replyPreview(JSONObject m){String body=m==null?"":m.optString("body").replaceFirst("^[🎤📷🎥🎬]\\s*","").trim();if(!body.isEmpty())return body;String t=m==null?"":m.optString("type");if("audio".equals(t))return"Voice message";if("image".equals(t))return"Photo";if("video".equals(t))return"Video";if("file".equals(t))return"File";if("shared_reel".equals(t))return"Reel";if("shared_post".equals(t))return"Post";return"Message";}

    private ImageButton actionIconButton(int drawable){ImageButton b=new ImageButton(this);b.setImageResource(drawable);b.setColorFilter(Color.WHITE);b.setScaleType(ImageView.ScaleType.CENTER);b.setPadding(dp(7),dp(7),dp(7),dp(7));b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private LinearLayout actionRow(String label,int drawable,boolean danger){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),0,dp(14),0);
        row.setMinimumHeight(dp(43));
        row.setBackgroundColor(Color.TRANSPARENT);

        if(drawable!=0){
            ImageView icon=new ImageView(this);
            icon.setImageResource(drawable);
            icon.setColorFilter(danger?Color.rgb(255,48,79):Color.WHITE);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon,new LinearLayout.LayoutParams(dp(20),dp(20)));
        }

        TextView text=text(
            label,
            14,
            danger?Color.rgb(255,48,79):Color.WHITE,
            Typeface.NORMAL
        );

        LinearLayout.LayoutParams tp=
            new LinearLayout.LayoutParams(0,dp(43),1);
        tp.leftMargin=drawable!=0?dp(11):0;
        row.addView(text,tp);
        return row;
    }
    private void animateMessageActionEmojiMenu(final View reactionsCard){
        if(reactionsCard==null)return;
        reactionsCard.animate().cancel();
        reactionsCard.setAlpha(0f);
        reactionsCard.setScaleX(.82f);
        reactionsCard.setScaleY(.82f);
        reactionsCard.setTranslationX(dp(22));
        reactionsCard.setTranslationY(dp(3));
        final long started=android.os.SystemClock.uptimeMillis();
        final long duration=215L;
        final Runnable[] frame=new Runnable[1];
        frame[0]=()->{
            long elapsed=android.os.SystemClock.uptimeMillis()-started;
            float t=Math.min(1f,elapsed/(float)duration);
            float ease=1f-(float)Math.pow(1f-t,3);
            float scale=.82f+(.18f*ease);
            reactionsCard.setAlpha(Math.min(1f,t*1.85f));
            reactionsCard.setScaleX(scale);
            reactionsCard.setScaleY(scale);
            reactionsCard.setTranslationX(dp(22)*(1f-ease));
            reactionsCard.setTranslationY(dp(3)*(1f-ease));
            if(t<1f)reactionsCard.postOnAnimation(frame[0]);
            else{
                reactionsCard.setAlpha(1f);
                reactionsCard.setScaleX(1f);
                reactionsCard.setScaleY(1f);
                reactionsCard.setTranslationX(0f);
                reactionsCard.setTranslationY(0f);
            }
        };
        reactionsCard.postOnAnimation(frame[0]);
    }

    private void showMessageActions(JSONObject m,View anchor){boolean mine=isMine(m),editable=mine&&"text".equals(m.optString("type"))&&!m.optString("body").trim().isEmpty();Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);Window actionWindow=d.getWindow();if(actionWindow!=null)actionWindow.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.argb(136,0,0,0));int screenW=getResources().getDisplayMetrics().widthPixels,screenH=getResources().getDisplayMetrics().heightPixels;int wide=Math.min(screenW-dp(24),dp(360)),actionW=Math.min((int)(screenW*.58f),dp(218));int[] loc=new int[2];if(anchor!=null)anchor.getLocationOnScreen(loc);int top=Math.max(dp(72),Math.min(screenH-dp(390),loc[1]-dp(68)));LinearLayout bundle=new LinearLayout(this);bundle.setOrientation(LinearLayout.VERTICAL);FrameLayout.LayoutParams bundleLp=new FrameLayout.LayoutParams(wide,-2);bundleLp.leftMargin=(screenW-wide)/2;bundleLp.topMargin=top;overlay.addView(bundle,bundleLp);
        FrameLayout reactionsCard=new FrameLayout(this);reactionsCard.setBackground(bg(Color.rgb(36,39,43),34));LinearLayout.LayoutParams rcLp=new LinearLayout.LayoutParams(-1,dp(78));bundle.addView(reactionsCard,rcLp);TextView label=text("Tap + to customize reactions",12,Color.rgb(174,179,187),Typeface.NORMAL);label.setGravity(Gravity.CENTER);FrameLayout.LayoutParams labelLp=new FrameLayout.LayoutParams(-1,dp(25),Gravity.TOP);labelLp.topMargin=dp(3);reactionsCard.addView(label,labelLp);LinearLayout reactions=new LinearLayout(this);reactions.setGravity(Gravity.CENTER);FrameLayout.LayoutParams reactionsLp=new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM);reactionsLp.setMargins(dp(10),0,dp(10),dp(7));reactionsCard.addView(reactions,reactionsLp);String[] reacts={"❤️","👍","😂","😮","😢","😡","🎉","＋"};for(String e:reacts){TextView rb=text(e,e.equals("＋")?20:20,Color.WHITE,Typeface.NORMAL);rb.setGravity(Gravity.CENTER);rb.setBackgroundColor(Color.TRANSPARENT);reactions.addView(rb,new LinearLayout.LayoutParams(0,-1,1));rb.setOnClickListener(v->{d.dismiss();if(e.equals("＋"))showEmojiPickerForReaction(m);else react(m,e);});}
        LinearLayout actionCard=new LinearLayout(this);actionCard.setOrientation(LinearLayout.VERTICAL);actionCard.setPadding(0,dp(5),0,dp(5));actionCard.setBackground(bg(Color.argb(239,36,39,43),17));LinearLayout.LayoutParams acLp=new LinearLayout.LayoutParams(actionW,-2);acLp.topMargin=dp(8);acLp.gravity=mine?Gravity.END:Gravity.START;bundle.addView(actionCard,acLp);
        LinearLayout reply=actionRow("Reply",R.drawable.ic_msg_reply,false);actionCard.addView(reply);reply.setOnClickListener(v->{d.dismiss();setReply(m);messageInput.requestFocus();});if(editable){LinearLayout edit=actionRow("Edit",R.drawable.ic_msg_edit,false);actionCard.addView(edit);edit.setOnClickListener(v->{d.dismiss();editMessage(m);});}LinearLayout forward=actionRow("Forward",R.drawable.ic_msg_forward,false);actionCard.addView(forward);forward.setOnClickListener(v->{d.dismiss();forwardMessagePicker(m);});if("text".equals(m.optString("type"))&&!m.optString("body").isEmpty()){LinearLayout copy=actionRow("Copy",R.drawable.ic_msg_copy,false);actionCard.addView(copy);copy.setOnClickListener(v->{d.dismiss();ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("message",m.optString("body")));toast("Copied");});}LinearLayout del=mine
            ?actionRow("Unsend",R.drawable.ic_msg_unsend,true)
            :actionRow("Delete for me",R.drawable.ic_msg_delete_me,false);actionCard.addView(del);del.setOnClickListener(v->{d.dismiss();deleteMessage(m,mine);});
        bundle.setClickable(true);
        reactionsCard.setClickable(true);
        actionCard.setClickable(true);
        overlay.setClickable(true);

        Runnable dismissAnimated=()->{
            bundle.animate().cancel();
            bundle.animate()
                .alpha(0f)
                .scaleX(.96f)
                .scaleY(.96f)
                .translationY(dp(7))
                .setDuration(105)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(d::dismiss)
                .start();
        };

        overlay.setOnClickListener(v->dismissAnimated.run());
        bundle.setOnClickListener(v->dismissAnimated.run());
        reactionsCard.setOnClickListener(v->{});
        actionCard.setOnClickListener(v->{});
reactionsCard.animate().cancel();
        reactionsCard.setAlpha(0f);
        reactionsCard.setScaleX(.76f);
        reactionsCard.setScaleY(.76f);
        reactionsCard.setTranslationY(dp(7));
        d.setContentView(overlay);
        Window w=d.getWindow();
        d.show();
        reactionsCard.postDelayed(
            ()->animateMessageActionEmojiMenu(reactionsCard),
            45
        );
        
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setDimAmount(0f);
            w.setLayout(-1,-1);
        }

        bundle.setAlpha(1f);
        bundle.setScaleX(1f);
        bundle.setScaleY(1f);
        bundle.setTranslationY(0f);

        actionCard.setAlpha(1f);
        actionCard.setScaleX(1f);
        actionCard.setScaleY(1f);
        actionCard.setTranslationY(0f);

        android.view.animation.OvershootInterpolator emojiBounce=
            new android.view.animation.OvershootInterpolator(.78f);

        
    }
    private void react(JSONObject m,String emoji){
        if(m==null)return;

        JSONArray before;
        try{
            JSONArray existing=m.optJSONArray("reactions");
            before=existing==null?new JSONArray():new JSONArray(existing.toString());
        }catch(Exception e){
            before=new JSONArray();
        }

        JSONArray next=new JSONArray();
        try{
            JSONArray current=m.optJSONArray("reactions");
            if(current!=null){
                for(int i=0;i<current.length();i++){
                    JSONObject r=current.optJSONObject(i);
                    if(r==null)continue;

                    String uid=r.optString("userId",r.optString("userid"));
                    boolean mine=r.optBoolean("mine")||(!selfId.isEmpty()&&selfId.equals(uid));
                    if(!mine)next.put(new JSONObject(r.toString()));
                }
            }

            if(emoji!=null&&!emoji.isEmpty()){
                JSONObject mineReaction=new JSONObject()
                    .put("emoji",emoji)
                    .put("mine",true);
                if(!selfId.isEmpty())mineReaction.put("userId",selfId);
                mineReaction.put("name","You");
                next.put(mineReaction);
            }

            m.put("reactions",next);
        }catch(Exception ignored){}

        if(messageAdapter!=null){
            messageAdapter.notifyDataSetChanged();
            if(list!=null)list.invalidateViews();
        }
        cacheMessagesNow();

        final JSONArray rollback=before;

        try{
            api.post(
                "/api/messaging/messages/"+m.optString("id")+"/reaction",
                new JSONObject().put("emoji",emoji==null?"":emoji),
                (json,error)->main.post(()->{
                    if(error!=null){
                        try{m.put("reactions",rollback);}catch(Exception ignored){}
                        if(messageAdapter!=null){
                            messageAdapter.notifyDataSetChanged();
                            if(list!=null)list.invalidateViews();
                        }
                        cacheMessagesNow();
                        toast(error.getMessage());
                        return;
                    }

                    JSONObject n=json.optJSONObject("message");
                    if(n!=null){
                        JSONObject merged=mergeMessage(m,n);
                        upsertMessage(merged);
                        cacheMessagesNow();
                    }
                })
            );
        }catch(Exception e){
            try{m.put("reactions",rollback);}catch(Exception ignored){}
            if(messageAdapter!=null){
                messageAdapter.notifyDataSetChanged();
                if(list!=null)list.invalidateViews();
            }
            cacheMessagesNow();
            toast(e.getMessage());
        }
    }

    private JSONObject mergeMessage(JSONObject oldMessage,JSONObject update){
        try{
            JSONObject merged=new JSONObject(oldMessage==null?"{}":oldMessage.toString());
            boolean keepStickerVisual=oldMessage!=null&&isStickerMessage(oldMessage);
            JSONArray stickerVisual=keepStickerVisual?oldMessage.optJSONArray("attachments"):null;

            if(update!=null){
                java.util.Iterator<String> keys=update.keys();
                while(keys.hasNext()){
                    String k=keys.next();
                    Object value=update.opt(k);
                    if(value!=null&&value!=JSONObject.NULL)merged.put(k,value);
                }
            }

            if(keepStickerVisual&&stickerVisual!=null&&stickerVisual.length()>0){
                merged.put("attachments",new JSONArray(stickerVisual.toString()));
                merged.put("sticker",true);
            }
            return merged;
        }catch(Exception e){
            return update!=null?update:oldMessage;
        }
    }
    private void showEmojiPickerForReaction(JSONObject m){showEmojiPickerInternal(emoji->react(m,emoji));}
    private String reactionAvatar(JSONObject reaction){
        if(reaction==null)return"";

        String direct=avatarUrl(reaction);
        if(!direct.isEmpty())return direct;

        JSONObject user=reaction.optJSONObject("user");
        String nested=avatarUrl(user);
        if(!nested.isEmpty())return nested;

        JSONObject sender=reaction.optJSONObject("sender");
        nested=avatarUrl(sender);
        if(!nested.isEmpty())return nested;

        boolean mine=reaction.optBoolean("mine");
        String uid=reaction.optString("userId",reaction.optString("userid"));
        if(mine||(!selfId.isEmpty()&&selfId.equals(uid))){
            if(activeConversation!=null){
                JSONArray ps=activeConversation.optJSONArray("participants");
                if(ps!=null){
                    for(int i=0;i<ps.length();i++){
                        JSONObject person=ps.optJSONObject(i);
                        if(person!=null&&person.optBoolean("isSelf")){
                            String mineAvatar=avatarUrl(person);
                            if(!mineAvatar.isEmpty())return mineAvatar;
                        }
                    }
                }
            }
        }
        return"";
    }

    private void showReactionDetails(JSONObject m){
        JSONArray reactions=m.optJSONArray("reactions");if(reactions==null||reactions.length()==0)return;
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.argb(170,0,0,0));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(10),dp(16),dp(16));card.setBackground(topBg(Color.rgb(23,28,33),28));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);overlay.addView(card,cp);
        View handle=new View(this);handle.setBackground(bg(Color.rgb(155,161,170),4));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(55),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(16);card.addView(handle,hp);
        TextView title=text("Reactions",18,Color.WHITE,Typeface.BOLD);card.addView(title,new LinearLayout.LayoutParams(-1,dp(36)));
        for(int i=0;i<reactions.length();i++){
            JSONObject r=reactions.optJSONObject(i);if(r==null)continue;String uid=r.optString("userId",r.optString("userid")),name=r.optString("name");boolean mine=r.optBoolean("mine")||(!selfId.isEmpty()&&selfId.equals(uid));if(name.isEmpty())name=mine?"You":"Facebook user";String av=reactionAvatar(r);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(3),dp(7),dp(3),dp(7));View avatar=buildUserAvatar(av,name,48);row.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setTranslationY(dp(3));LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,dp(52),1);nlp.leftMargin=dp(11);row.addView(names,nlp);names.addView(text(name,17,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(29)));if(mine)names.addView(text("Tap to remove",13,Color.rgb(174,179,187),Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(21)));TextView emo=text(r.optString("emoji"),22,Color.WHITE,Typeface.NORMAL);emo.setGravity(Gravity.CENTER);row.addView(emo,new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(row,new LinearLayout.LayoutParams(-1,dp(62)));if(mine)row.setOnClickListener(v->{d.dismiss();react(m,"");});
        }
        final float[] dragStart={Float.NaN};
        View.OnTouchListener sheetDrag=(v,e)->{switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:dragStart[0]=e.getRawY()-card.getTranslationY();card.animate().cancel();v.getParent().requestDisallowInterceptTouchEvent(true);return true;case MotionEvent.ACTION_MOVE:if(Float.isNaN(dragStart[0]))return true;float dy=Math.max(0,e.getRawY()-dragStart[0]);card.setTranslationY(dy);float ratio=Math.min(1f,dy/Math.max(dp(260f),card.getHeight()*.7f));overlay.setBackgroundColor(Color.argb((int)(170*(1f-ratio)),0,0,0));return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(Float.isNaN(dragStart[0]))return true;float y=card.getTranslationY();dragStart[0]=Float.NaN;v.getParent().requestDisallowInterceptTouchEvent(false);if(y>dp(82)){card.animate().translationY(Math.max(card.getHeight(),dp(420))).setDuration(160).withEndAction(d::dismiss).start();}else{card.animate().translationY(0).setDuration(165).start();overlay.setBackgroundColor(Color.argb(170,0,0,0));}return true;}return true;};
        View.OnTouchListener cardDrag=(v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&e.getY()>dp(74))return false;return sheetDrag.onTouch(v,e);};card.setOnTouchListener(cardDrag);handle.setOnTouchListener(sheetDrag);title.setOnTouchListener(sheetDrag);
        overlay.setOnClickListener(v->{if(v==overlay)d.dismiss();});d.setContentView(overlay);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(0f);w.setLayout(-1,-1);}
    }
    private void forwardMessagePicker(JSONObject m){api.get("/api/messaging/contacts?q=",(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONArray ar=json.optJSONArray("contacts");if(ar==null||ar.length()==0){toast("No people found.");return;}String[] names=new String[ar.length()];for(int i=0;i<ar.length();i++)names[i]=ar.optJSONObject(i).optString("name","Facebook user");new AlertDialog.Builder(this).setTitle("Forward message").setItems(names,(dd,w)->{JSONObject c=ar.optJSONObject(w);try{api.post("/api/messaging/conversations",new JSONObject().put("type","direct").put("userId",c.optString("id")),(cj,ce)->main.post(()->{if(ce!=null){toast(ce.getMessage());return;}JSONObject conv=cj.optJSONObject("conversation");if(conv==null)return;try{api.post("/api/messaging/messages/"+m.optString("id")+"/forward",new JSONObject().put("conversationId",conv.optString("id")),(fj,fe)->main.post(()->toast(fe==null?"Message forwarded":fe.getMessage())));}catch(Exception ex){toast(ex.getMessage());}}));}catch(Exception ex){toast(ex.getMessage());}}).show();}));}
    private void editMessage(JSONObject m){EditText input=new EditText(this);input.setText(m.optString("body"));input.setSelection(input.length());new AlertDialog.Builder(this).setTitle("Edit message").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String b=input.getText().toString().trim();if(b.isEmpty())return;try{api.patch("/api/messaging/messages/"+m.optString("id"),new JSONObject().put("body",b),(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject n=json.optJSONObject("message");if(n!=null){upsertMessage(n);cacheMessagesNow();}}));}catch(Exception e){toast(e.getMessage());}}).show();}
    private void deleteMessage(JSONObject m,boolean everyone){api.delete("/api/messaging/messages/"+m.optString("id")+"?scope="+(everyone?"everyone":"me"),(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}if(everyone&&json.optJSONObject("message")!=null)upsertMessage(json.optJSONObject("message"));else messages.removeIf(x->x.optString("id").equals(m.optString("id")));messageAdapter.notifyDataSetChanged();cacheMessagesNow();}));}

    interface EmojiConsumer{void accept(String emoji);}
    private void showEmojiPicker(){showEmojiPickerInternal(emoji->{messageInput.append(emoji);messageInput.requestFocus();});}
    private void showEmojiPickerInternal(EmojiConsumer consumer){Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(Color.WHITE);String[][] groups={
        {"Recent","😀 😂 ❤️ 👍 😍 🥰 😭 🔥 🎉 🙏"},
        {"Smileys","😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫣 🤭 🫢 🫡 🤫 🫠 🤥 😶 🫥 😐 🫤 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 😵‍💫 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕"},
        {"People","👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 👀 👁️ 💋 👶 🧒 👦 👧 🧑 👱 👨 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷 👮 👷 💂 🕵️ 👩‍⚕️ 👩‍🎓 👩‍💻 👰 🤵 👸 🤴 🥷 🦸 🦹 🧙 🧚"},
        {"Animals","🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐠 🐟 🐬 🐳 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦒 🦘 🐕 🐈 🐇 🦝 🦦 🦥 🦔"},
        {"Food","🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🍞 🥖 🧀 🥚 🍳 🥞 🧇 🥓 🥩 🍗 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍚 🍰 🎂 🍭 🍬 🍫 🍿 🍩 🍪 ☕ 🍵 🧃 🥤 🧋 🍺 🍷"},
        {"Activities","⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🏒 🏑 🥊 🥋 🛹 ⛸️ 🎿 🏂 🏋️ 🤸 ⛹️ 🏌️ 🏄 🏊 🚴 🏆 🥇 🥈 🥉 🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🧩"},
        {"Travel","🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚚 🚛 🚜 🏍️ 🛵 🚲 🛴 🚨 🚡 🚃 🚄 🚅 🚂 🚆 🚇 ✈️ 🛫 🛬 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛳️ 🚢 ⚓ ⛽ 🚧 🚦 🗺️ 🗽 🗼 🏰 🏟️ 🎡 🎢 ⛲ 🏖️ 🏝️ 🌋 ⛰️ 🏕️ ⛺ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 🕌"},
        {"Objects","⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 🎮 💽 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️ 🎧 🧭 ⏱️ ⏰ 📡 🔋 🔌 💡 🔦 💸 💵 💳 💎 ⚖️ 🧰 🔧 🔨 ⚙️ 🔪 🛡️ 🔮 🔭 🔬 🩺 💊 💉 🧬 🔑 🚪 🪑 🛋️ 🛏️ 🧸 🖼️ 🪞 🛍️ 🎁 🎈 🎀 ✉️ 📩 📧 💌 📦 🏷️ 📜 📄 📊 📈 📉 🗓️ 📅 🗑️ 📁 📂 📰 📚 📖 🔖 📎 📌 ✂️ 🖊️ ✏️ 🔍"},
        {"Symbols","❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 ❣️ 💕 💞 💓 💗 💖 💘 💝 ☮️ ✝️ ☪️ 🕉️ ☯️ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ ☢️ 📴 📳 🅰️ 🅱️ 🆘 ❌ ⭕ 🛑 ⛔ 🚫 💯 ❗ ❓ ⚠️ ♻️ ✅ ❎ 🌐 💤 🚾 ♿ 🅿️ ℹ️ 🆗 🆙 🆒 🆕 🆓 ▶️ ⏸️ ⏯️ ⏹️ ⏭️ ⏮️ ⏩ ⏪ 🔀 🔁 ◀️ 🔼 🔽 ➡️ ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️ ↖️ ↔️ 🔄 ↩️"},
        {"Flags","🏳️ 🏴 🏁 🚩 🏳️‍🌈 🏳️‍⚧️ 🏴‍☠️ 🇵🇸 🇯🇴 🇪🇬 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇧🇭 🇴🇲 🇱🇧 🇸🇾 🇮🇶 🇹🇷 🇬🇧 🇺🇸 🇨🇦 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇮🇪 🇬🇷 🇵🇱 🇺🇦 🇷🇺 🇨🇳 🇯🇵 🇰🇷 🇮🇳 🇵🇰 🇧🇩 🇮🇩 🇲🇾 🇸🇬 🇹🇭 🇻🇳 🇵🇭 🇦🇺 🇳🇿 🇧🇷 🇦🇷 🇲🇽 🇿🇦 🇳🇬 🇲🇦 🇩🇿 🇹🇳"}
    };HorizontalScrollView tabsScroll=new HorizontalScrollView(this);LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);tabsScroll.addView(tabs,new HorizontalScrollView.LayoutParams(-2,dp(42)));ScrollView scroll=new ScrollView(this);GridLayout grid=new GridLayout(this);grid.setColumnCount(8);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));Runnable[] render=new Runnable[1];final int[] selected={0};render[0]=()->{grid.removeAllViews();String[] all=groups[selected[0]][1].split(" ");for(String e:all){Button b=new Button(this);b.setText(e);b.setTextSize(24);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(0,0,0,0);GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(44);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);grid.addView(b,lp);b.setOnClickListener(v->{consumer.accept(e);d.dismiss();});}};for(int i=0;i<groups.length;i++){final int ix=i;Button tb=new Button(this);tb.setText(groups[i][0]);tb.setTextSize(11);tb.setAllCaps(false);tb.setBackgroundColor(Color.TRANSPARENT);tb.setTextColor(i==0?BLUE:SUB);tabs.addView(tb,new LinearLayout.LayoutParams(-2,dp(40)));tb.setOnClickListener(v->{selected[0]=ix;for(int j=0;j<tabs.getChildCount();j++)((Button)tabs.getChildAt(j)).setTextColor(j==ix?BLUE:SUB);render[0].run();});}panel.addView(tabsScroll,new LinearLayout.LayoutParams(-1,dp(42)));panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(330)));render[0].run();d.setContentView(panel);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);}d.show();if(w!=null)w.setLayout(-1,-2);}
    private void showMessengerStickerPicker(){
        if(root==null||composer==null||activeConversation==null)return;
        View mediaOld=root.findViewWithTag("messenger-native-media-sheet");
        if(mediaOld!=null){
            View mediaBridge=root.findViewWithTag("messenger-media-bridge");
            if(mediaOld.getParent()==root)root.removeView(mediaOld);
            if(mediaBridge!=null&&mediaBridge.getParent()==root)root.removeView(mediaBridge);
            if(composer!=null)composer.setTranslationY(0);
            if(replyBar!=null)replyBar.setTranslationY(0);
            if(list!=null){
                list.setTranslationY(0);
                list.setPadding(dp(10),dp(12),dp(10),dp(5));
            }
        }

        // Close any previous native sticker surface first.
        View old=root.findViewWithTag("messenger-native-sticker-sheet");
        if(old!=null){ dismissMessengerStickerPicker(old); return; }
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null&&messageInput!=null)imm.hideSoftInputFromWindow(messageInput.getWindowToken(),0);

        final int sheetH=Math.min(
            dp(500),
            (int)(getResources().getDisplayMetrics().heightPixels*.58f)
        );
        FrameLayout host=new FrameLayout(this);host.setTag("messenger-native-sticker-sheet");host.setClickable(true);host.setBackgroundColor(Color.rgb(38,38,38));
        FrameLayout.LayoutParams hostLp=new FrameLayout.LayoutParams(-1,sheetH,Gravity.BOTTOM);root.addView(host,hostLp);
        View stickerBridge=new View(this);stickerBridge.setTag("messenger-sticker-bridge");stickerBridge.setBackgroundColor(Color.TRANSPARENT);FrameLayout.LayoutParams bridgeLp=new FrameLayout.LayoutParams(-1,dp(4),Gravity.BOTTOM);bridgeLp.bottomMargin=sheetH-dp(1);root.addView(stickerBridge,bridgeLp);

        LinearLayout sheet=new LinearLayout(this);sheet.setBackground(topBg(Color.rgb(38,38,38),14));sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(10),dp(6),dp(10),dp(8));host.addView(sheet,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout dragHeader=new LinearLayout(this);dragHeader.setOrientation(LinearLayout.VERTICAL);dragHeader.setGravity(Gravity.CENTER_HORIZONTAL);dragHeader.setPadding(0,dp(1),0,dp(6));sheet.addView(dragHeader,new LinearLayout.LayoutParams(-1,dp(64)));
        View puller=new View(this);puller.setBackground(bg(Color.rgb(190,193,199),3));LinearLayout.LayoutParams php=new LinearLayout.LayoutParams(dp(42),dp(5));php.gravity=Gravity.CENTER_HORIZONTAL;php.bottomMargin=dp(8);dragHeader.addView(puller,php);
        LinearLayout searchWrap=new LinearLayout(this);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(dp(11),0,dp(10),0);
        searchWrap.setBackground(bg(Color.rgb(54,54,54),18));
        ImageView searchIcon=new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_sticker_search);
        searchIcon.setColorFilter(Color.rgb(159,167,178));
        searchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        searchWrap.addView(searchIcon,new LinearLayout.LayoutParams(dp(23),dp(23)));
        EditText search=new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search stickers");
        search.setHintTextColor(Color.rgb(159,167,178));
        search.setTextColor(Color.WHITE);
        search.setTextSize(15);
        search.setPadding(dp(7),0,dp(5),0);
        search.setBackgroundColor(Color.TRANSPARENT);
        searchWrap.addView(search,new LinearLayout.LayoutParams(0,dp(42),1));
        dragHeader.addView(searchWrap,new LinearLayout.LayoutParams(-1,dp(42)));

        FrameLayout gridHost=new FrameLayout(this);sheet.addView(gridHost,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setPadding(0,0,0,dp(8));GridLayout grid=new GridLayout(this);grid.setColumnCount(3);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));gridHost.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        ProgressBar loading=new ProgressBar(this);gridHost.addView(loading,new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER));

        final int[] requestId={0};
        final View.OnTouchListener[] stickerContentDrag=new View.OnTouchListener[1];
        class Loader{void load(String query){int my=++requestId[0];loading.setVisibility(View.VISIBLE);grid.removeAllViews();String q=query==null?"":query.trim();String url=q.isEmpty()?"https://api.giphy.com/v1/stickers/trending?api_key="+Uri.encode(COMMENT_STICKER_API_KEY)+"&limit=50&rating=g":"https://api.giphy.com/v1/stickers/search?api_key="+Uri.encode(COMMENT_STICKER_API_KEY)+"&q="+Uri.encode(q)+"&limit=50&offset=0&rating=g&lang=en";api.get(url,(json,error)->main.post(()->{if(my!=requestId[0]||host.getParent()==null)return;loading.setVisibility(View.GONE);if(error!=null){TextView err=text("Could not load stickers. Try again.",14,SUB,Typeface.NORMAL);err.setGravity(Gravity.CENTER);grid.addView(err,new GridLayout.LayoutParams(GridLayout.spec(0),GridLayout.spec(0,3)));return;}JSONArray items=json.optJSONArray("data");if(items==null||items.length()==0){TextView empty=text("No stickers found",14,SUB,Typeface.NORMAL);empty.setGravity(Gravity.CENTER);grid.addView(empty,new GridLayout.LayoutParams(GridLayout.spec(0),GridLayout.spec(0,3)));return;}for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;JSONObject imgs=item.optJSONObject("images");String src="";if(imgs!=null){String[] keys={"original","downsized","fixed_width","fixed_height","fixed_width_small","fixed_height_small"};for(String k:keys){JSONObject im=imgs.optJSONObject(k);if(im!=null){src=im.optString("url",im.optString("webp"));if(!src.isEmpty())break;}}}if(src.isEmpty())continue;FrameLayout cell=new FrameLayout(MainActivity.this);cell.setBackgroundColor(Color.TRANSPARENT);GridLayout.LayoutParams cp=new GridLayout.LayoutParams();cp.width=0;cp.height=dp(132);cp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);cp.setMargins(dp(2),0,dp(2),0);grid.addView(cell,cp);ImageView iv=new ImageView(MainActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);iv.setBackgroundColor(Color.TRANSPARENT);cell.addView(iv,new FrameLayout.LayoutParams(dp(124),dp(124),Gravity.CENTER));stickers.load(src,iv);final String chosen=src;cell.setOnClickListener(v->{sendStickerFromUrl(chosen);});if(stickerContentDrag[0]!=null)cell.setOnTouchListener(stickerContentDrag[0]);}}));}}
        Loader loader=new Loader();final Runnable searchTask=()->loader.load(search.getText().toString());search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){main.removeCallbacks(searchTask);main.postDelayed(searchTask,220);}public void afterTextChanged(Editable e){}});

        final float[] startY={Float.NaN}; final boolean[] dragged={false};
        View.OnTouchListener drag=(v,e)->{switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:startY[0]=e.getRawY()-host.getTranslationY();dragged[0]=false;host.animate().cancel();composer.animate().cancel();if(replyBar!=null)replyBar.animate().cancel();v.getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:if(Float.isNaN(startY[0]))return true;float dy=Math.max(0,e.getRawY()-startY[0]);if(dy>dp(3))dragged[0]=true;host.setTranslationY(dy);stickerBridge.setTranslationY(dy);composer.setTranslationY(-sheetH+dy);applyConversationPickerInset(Math.max(0,sheetH-(int)dy));if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE)replyBar.setTranslationY(-sheetH+dy);return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:float y=host.getTranslationY();startY[0]=Float.NaN;v.getParent().requestDisallowInterceptTouchEvent(false);if(y>dp(48)){dismissMessengerStickerPicker(host);}else{host.animate().translationY(0).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();stickerBridge.animate().translationY(0).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();applyConversationPickerInset(sheetH);applyConversationPickerInset(sheetH);composer.animate().translationY(-sheetH).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE)replyBar.animate().translationY(-sheetH).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();}return true;
        }return true;};
        // The whole puller/search header is the drag target, including the search-bar area.
        dragHeader.setOnTouchListener(drag);
        // Keep search usable: a short tap focuses it, vertical movement drags the sheet.
        search.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startY[0]=e.getRawY()-host.getTranslationY();dragged[0]=false;return false;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getRawY()-startY[0]-host.getTranslationY())>dp(8)){search.clearFocus();if(imm!=null)imm.hideSoftInputFromWindow(search.getWindowToken(),0);return drag.onTouch(dragHeader,e);}if((e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)&&dragged[0])return drag.onTouch(dragHeader,e);return false;});

        // At the top of the sticker list, a downward gesture from the scroll area or any sticker cell drags the whole sheet.
        final float[] contentDownY={Float.NaN};final boolean[] contentSheetDrag={false};
        View.OnTouchListener contentDrag=(v,e)->{switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:contentDownY[0]=e.getRawY();contentSheetDrag[0]=false;return false;
            case MotionEvent.ACTION_MOVE:float delta=e.getRawY()-contentDownY[0];if(!contentSheetDrag[0]&&scroll.getScrollY()==0&&delta>dp(5)){contentSheetDrag[0]=true;startY[0]=contentDownY[0]-host.getTranslationY();dragged[0]=true;host.animate().cancel();stickerBridge.animate().cancel();composer.animate().cancel();if(replyBar!=null)replyBar.animate().cancel();ViewParent parent=v.getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);}if(contentSheetDrag[0]){float dy=Math.max(0,e.getRawY()-startY[0]);host.setTranslationY(dy);stickerBridge.setTranslationY(dy);composer.setTranslationY(-sheetH+dy);applyConversationPickerInset(Math.max(0,sheetH-(int)dy));if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE)replyBar.setTranslationY(-sheetH+dy);return true;}return false;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(contentSheetDrag[0]){float y=host.getTranslationY();contentSheetDrag[0]=false;contentDownY[0]=Float.NaN;ViewParent parent=v.getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);if(y>dp(64))dismissMessengerStickerPicker(host);else{host.animate().translationY(0).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();stickerBridge.animate().translationY(0).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();applyConversationPickerInset(sheetH);applyConversationPickerInset(sheetH);composer.animate().translationY(-sheetH).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE)replyBar.animate().translationY(-sheetH).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();}return true;}contentDownY[0]=Float.NaN;return false;}return false;};
        stickerContentDrag[0]=contentDrag;scroll.setOnTouchListener(contentDrag);grid.setOnTouchListener(contentDrag);

        host.setTranslationY(sheetH);stickerBridge.setTranslationY(sheetH);composer.setTranslationY(0);if(replyBar!=null)replyBar.setTranslationY(0);if(list!=null){applyConversationPickerInset(sheetH);list.post(()->list.setSelection(Math.max(0,list.getCount()-1)));}host.animate().translationY(0).setDuration(330).setInterpolator(new DecelerateInterpolator()).start();stickerBridge.animate().translationY(0).setDuration(330).setInterpolator(new DecelerateInterpolator()).start();applyConversationPickerInset(sheetH);applyConversationPickerInset(sheetH);composer.animate().translationY(-sheetH).setDuration(330).setInterpolator(new DecelerateInterpolator()).start();if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE)replyBar.animate().translationY(-sheetH).setDuration(330).setInterpolator(new DecelerateInterpolator()).start();
        loader.load("");
    }
    private void dismissMessengerStickerPicker(View host){if(host==null||root==null)return;View bridge=root.findViewWithTag("messenger-sticker-bridge");int h=Math.max(host.getHeight(),dp(360));host.animate().translationY(h).setDuration(220).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{if(host.getParent()==root)root.removeView(host);}).start();if(bridge!=null)bridge.animate().translationY(h).setDuration(220).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{if(bridge.getParent()==root)root.removeView(bridge);}).start();if(composer!=null)composer.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();if(list!=null)list.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();if(replyBar!=null)replyBar.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();if(list!=null)list.setPadding(dp(10),dp(12),dp(10),dp(5));}

    private JSONObject buildOptimisticSticker(String url,String client,JSONObject reply){JSONObject temp=new JSONObject();try{temp.put("id","tmp-"+System.nanoTime());temp.put("clientId",client);temp.put("conversationId",activeConversation==null?"":activeConversation.optString("id"));temp.put("senderId",selfId);temp.put("type","image");temp.put("body","");temp.put("createdAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date()));temp.put("status","sending");temp.put("pending",true);temp.put("sticker",true);temp.put("sender",new JSONObject().put("id",selfId).put("name","You").put("isSelf",true));temp.put("attachments",new JSONArray().put(new JSONObject().put("url",url).put("mime","image/gif").put("name","sticker.gif").put("sticker",true)));temp.put("reactions",new JSONArray());if(reply!=null)temp.put("reply",new JSONObject().put("id",reply.optString("id")).put("body",reply.optString("body")).put("type",reply.optString("type","text")).put("senderName",senderName(reply)));}catch(Exception ignored){}return temp;}
    private void sendStickerFromUrl(String url){if(activeConversation==null||url==null||url.isEmpty())return;final String cid=activeConversation.optString("id"),client="sticker-native-"+UUID.randomUUID();stickerLastConversations.add(cid);final JSONObject replyObj=replyTo;final String reply=replyObj==null?"":replyObj.optString("id");JSONObject temp=buildOptimisticSticker(url,client,replyObj);messages.add(temp);if(messageAdapter!=null){messageAdapter.notifyDataSetChanged();scrollToAbsoluteBottom();}cacheMessagesNow();if(replyObj!=null)setReply(null);new Thread(()->{try{byte[] bytes=stickers.getCachedOrFetch(url);if(bytes==null||bytes.length==0)throw new Exception("Could not load sticker.");api.upload("/api/messaging/conversations/"+cid+"/attachment",bytes,"sticker-"+System.currentTimeMillis()+".gif","image/gif","",client,reply,(json,error)->main.post(()->{if(error!=null){markOptimisticFailed(client);toast(error.getMessage());return;}JSONObject m=json.optJSONObject("message");if(m!=null){try{m.put("sticker",true);}catch(Exception ignored){}stickerLastConversations.add(cid);replaceOptimistic(client,m);cacheMessagesNow();refreshInbox();}}));}catch(Exception ex){main.post(()->{markOptimisticFailed(client);toast(ex.getMessage());});}}).start();}


    private void closePickersOnly(){
        if(root==null)return;

        View media=root.findViewWithTag("messenger-native-media-sheet");
        View mediaBridge=root.findViewWithTag("messenger-media-bridge");
        if(media!=null&&media.getParent()==root)root.removeView(media);
        if(mediaBridge!=null&&mediaBridge.getParent()==root)root.removeView(mediaBridge);

        View stickersView=root.findViewWithTag("messenger-native-sticker-sheet");
        View stickerBridge=root.findViewWithTag("messenger-sticker-bridge");
        if(stickersView!=null&&stickersView.getParent()==root)root.removeView(stickersView);
        if(stickerBridge!=null&&stickerBridge.getParent()==root)root.removeView(stickerBridge);

        if(composer!=null){
            composer.animate().cancel();
            composer.setTranslationY(0f);
        }
        if(replyBar!=null){
            replyBar.animate().cancel();
            replyBar.setTranslationY(0f);
        }
        if(list!=null){
            list.animate().cancel();
            list.setTranslationY(0f);
            list.setPadding(dp(10),dp(12),dp(10),dp(5));
        }
    }

    private void closePickersAndOpenKeyboard(){
        closePickersOnly();
        if(messageInput==null)return;
        messageInput.requestFocus();
        main.postDelayed(()->{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showSoftInput(messageInput,InputMethodManager.SHOW_IMPLICIT);
        },120);
    }

    private String conversationName(){
        if(activeConversation==null)return "User";
        String n=activeConversation.optString("name","");
        if(n.isEmpty())n=activeConversation.optString("title","");
        return n.isEmpty()?"User":n;
    }

    private LinearLayout messageTitle(){
        LinearLayout title=new LinearLayout(this);
        title.setGravity(Gravity.CENTER);
        title.addView(text("Message ",18,Color.WHITE,Typeface.NORMAL));
        title.addView(text(conversationName(),18,Color.WHITE,Typeface.BOLD));
        return title;
    }

    private void openMessageCamera(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
            return;
        }
        showMessageCamera();
    }

    private int currentDisplayDegrees(){int rotation=getWindowManager().getDefaultDisplay().getRotation();if(rotation==android.view.Surface.ROTATION_90)return 90;if(rotation==android.view.Surface.ROTATION_180)return 180;if(rotation==android.view.Surface.ROTATION_270)return 270;return 0;}
    private int cameraPreviewOrientation(android.hardware.Camera.CameraInfo info){int degrees=currentDisplayDegrees();if(info.facing==android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT){int result=(info.orientation+degrees)%360;return(360-result)%360;}return(info.orientation-degrees+360)%360;}
    private int cameraJpegOrientation(android.hardware.Camera.CameraInfo info){int degrees=currentDisplayDegrees();return info.facing==android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT?(info.orientation+degrees)%360:(info.orientation-degrees+360)%360;}
    private byte[] normalizeCapturedJpeg(byte[] jpeg){if(jpeg==null||jpeg.length==0)return jpeg;try{ExifInterface exif=new ExifInterface(new ByteArrayInputStream(jpeg));int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);if(orientation==ExifInterface.ORIENTATION_NORMAL||orientation==ExifInterface.ORIENTATION_UNDEFINED)return jpeg;Bitmap source=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(source==null)return jpeg;Matrix matrix=new Matrix();if(orientation==ExifInterface.ORIENTATION_FLIP_HORIZONTAL)matrix.setScale(-1f,1f);else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)matrix.setRotate(180f);else if(orientation==ExifInterface.ORIENTATION_FLIP_VERTICAL)matrix.setScale(1f,-1f);else if(orientation==ExifInterface.ORIENTATION_TRANSPOSE){matrix.setRotate(90f);matrix.postScale(-1f,1f);}else if(orientation==ExifInterface.ORIENTATION_ROTATE_90)matrix.setRotate(90f);else if(orientation==ExifInterface.ORIENTATION_TRANSVERSE){matrix.setRotate(-90f);matrix.postScale(-1f,1f);}else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)matrix.setRotate(-90f);Bitmap fixed=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);ByteArrayOutputStream out=new ByteArrayOutputStream();fixed.compress(Bitmap.CompressFormat.JPEG,95,out);if(fixed!=source)fixed.recycle();source.recycle();return out.toByteArray();}catch(Exception ignored){return jpeg;}}

    private void showMessageCamera(){
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout page=new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);

        android.view.SurfaceView surface=new android.view.SurfaceView(this);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-1);
        sp.bottomMargin=dp(112);
        page.addView(surface,sp);

        final android.hardware.Camera[] cam={null};
        final int[] facing={android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK};
        final boolean[] flashOn={false};

        Runnable release=()->{
            try{
                if(cam[0]!=null){
                    cam[0].stopPreview();
                    cam[0].release();
                }
            }catch(Exception ignored){}
            cam[0]=null;
        };

        final Runnable[] start={null};
        start[0]=()->{
            release.run();
            try{
                int id=0;
                android.hardware.Camera.CameraInfo info=new android.hardware.Camera.CameraInfo();
                for(int i=0;i<android.hardware.Camera.getNumberOfCameras();i++){
                    android.hardware.Camera.getCameraInfo(i,info);
                    if(info.facing==facing[0]){id=i;break;}
                }
                android.hardware.Camera.getCameraInfo(id,info);
                cam[0]=android.hardware.Camera.open(id);
                cam[0].setPreviewDisplay(surface.getHolder());
                cam[0].setDisplayOrientation(cameraPreviewOrientation(info));
                android.hardware.Camera.Parameters initial=cam[0].getParameters();
                initial.setRotation(cameraJpegOrientation(info));
                cam[0].setParameters(initial);
                cam[0].startPreview();
            }catch(Exception e){
                toast("Camera unavailable.");
            }
        };

        final float[] cameraSwipeDownY={Float.NaN};
        surface.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    cameraSwipeDownY[0]=e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!Float.isNaN(cameraSwipeDownY[0])){
                        float dy=e.getRawY()-cameraSwipeDownY[0];
                        cameraSwipeDownY[0]=Float.NaN;
                        if(dy<-dp(70)){
                            d.dismiss();
                            pickInstagramMediaFullScreen();
                            return true;
                        }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cameraSwipeDownY[0]=Float.NaN;
                    return true;
            }
            return true;
        });

        surface.getHolder().addCallback(new android.view.SurfaceHolder.Callback(){
            public void surfaceCreated(android.view.SurfaceHolder h){start[0].run();}
            public void surfaceChanged(android.view.SurfaceHolder h,int f,int w,int hh){}
            public void surfaceDestroyed(android.view.SurfaceHolder h){release.run();}
        });

        page.addView(messageTitle(),new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));

        ImageButton close=icon(R.drawable.ic_msg_close,46,Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams cp=
            new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.TOP|Gravity.START);
        cp.leftMargin=dp(12);
        cp.topMargin=dp(44);
        page.addView(close,cp);
        close.setOnClickListener(v->d.dismiss());

        ImageButton flash=icon(R.drawable.ic_camera_flash_off_exact_ref,46,Color.WHITE);
        flash.clearColorFilter();
        flash.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams fp=
            new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        fp.topMargin=dp(44);
        page.addView(flash,fp);
        flash.setOnClickListener(v->{
            try{
                if(cam[0]==null)return;
                android.hardware.Camera.Parameters params=cam[0].getParameters();
                List<String> modes=params.getSupportedFlashModes();
                if(modes==null)return;

                boolean next=!flashOn[0];
                String mode=next
                    ?android.hardware.Camera.Parameters.FLASH_MODE_TORCH
                    :android.hardware.Camera.Parameters.FLASH_MODE_OFF;

                if(!modes.contains(mode))return;
                params.setFlashMode(mode);
                cam[0].setParameters(params);
                flashOn[0]=next;

                flash.setImageResource(
                    flashOn[0]
                        ?R.drawable.ic_camera_flash_on_exact_ref
                        :R.drawable.ic_camera_flash_off_exact_ref
                );
                flash.clearColorFilter();
            }catch(Exception ignored){}
        });

        TextView aa=text("Aa",28,Color.WHITE,Typeface.NORMAL);
        aa.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ap=
            new FrameLayout.LayoutParams(dp(60),dp(60),Gravity.START|Gravity.TOP);
        ap.leftMargin=dp(16);
        ap.topMargin=dp(250);
        page.addView(aa,ap);
        aa.setOnClickListener(v->{d.dismiss();showMessageTextCreate();});

        FrameLayout shutter=new FrameLayout(this);
        GradientDrawable shutterOuter=new GradientDrawable();
        shutterOuter.setShape(GradientDrawable.OVAL);
        shutterOuter.setColor(Color.TRANSPARENT);
        shutterOuter.setStroke(dp(3),Color.WHITE);
        shutter.setBackground(shutterOuter);

        View shutterInner=new View(this);
        shutterInner.setBackground(bg(Color.WHITE,31));
        FrameLayout.LayoutParams innerLp=
            new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.CENTER);
        shutter.addView(shutterInner,innerLp);

        FrameLayout.LayoutParams shp=
            new FrameLayout.LayoutParams(dp(76),dp(76),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        shp.bottomMargin=dp(66);
        page.addView(shutter,shp);
        shutter.setOnClickListener(v->{
            try{
                if(cam[0]==null)return;
                cam[0].takePicture(null,null,(data,c)->{
                    if(data==null)return;
                    byte[] bytes=normalizeCapturedJpeg(data.clone());
                    d.dismiss();
                    main.post(()->showCapturedMediaPreview(bytes,this::showMessageCamera));
                });
            }catch(Exception e){
                toast("Couldn't take photo.");
            }
        });

        ImageButton gallery=icon(R.drawable.ic_camera_gallery_exact_ref,46,Color.WHITE);
        gallery.clearColorFilter();
        gallery.setScaleType(ImageView.ScaleType.CENTER_CROP);
        gallery.setBackground(bg(Color.rgb(38,38,38),11));
        gallery.setClipToOutline(true);
        loadLatestGalleryThumbnail(gallery);
        FrameLayout.LayoutParams gp=
            new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.BOTTOM|Gravity.START);
        gp.leftMargin=dp(22);
        gp.bottomMargin=dp(18);
        page.addView(gallery,gp);
        gallery.setOnClickListener(v->{d.dismiss();pickInstagramMediaFullScreen(1);});

        ImageButton flip=icon(R.drawable.ic_camera_switch,48,Color.WHITE);
        flip.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams swp=
            new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.END);
        swp.rightMargin=dp(22);
        swp.bottomMargin=dp(18);
        page.addView(flip,swp);
        flip.setOnClickListener(v->{
            facing[0]=facing[0]==android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK
                ?android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT
                :android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
            flashOn[0]=false;
            flash.setImageResource(R.drawable.ic_camera_flash_off_exact_ref);
            flash.clearColorFilter();
            start[0].run();
        });

        d.setOnDismissListener(x->release.run());
        d.setContentView(page);
        d.show();

        Window w=d.getWindow();
        if(w!=null){
            w.setLayout(-1,-1);
            w.setStatusBarColor(Color.BLACK);
            w.setNavigationBarColor(Color.BLACK);
        }
    }

    private void makePreviewTransformable(final View target){
        final float[] x={0},y={0},dist={0},downX={0},downY={0};
        final boolean[] scale={false},moved={false};
        target.setClickable(true);
        target.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    x[0]=downX[0]=e.getRawX();
                    y[0]=downY[0]=e.getRawY();
                    scale[0]=false;moved[0]=false;v.bringToFront();return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if(e.getPointerCount()>=2){
                        float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);
                        dist[0]=(float)Math.sqrt(dx*dx+dy*dy);scale[0]=true;moved[0]=true;
                    }return true;
                case MotionEvent.ACTION_MOVE:
                    if(e.getPointerCount()>=2){
                        float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);
                        float d=(float)Math.sqrt(dx*dx+dy*dy);
                        if(dist[0]>0){
                            float f=d/dist[0],n=Math.max(.35f,Math.min(4f,v.getScaleX()*f));
                            v.setScaleX(n);v.setScaleY(n);
                        }
                        dist[0]=d;scale[0]=true;moved[0]=true;
                    }else if(!scale[0]){
                        float nx=e.getRawX(),ny=e.getRawY();
                        float dx=nx-x[0],dy=ny-y[0];
                        if(Math.abs(nx-downX[0])>dp(4)||Math.abs(ny-downY[0])>dp(4))moved[0]=true;
                        v.setTranslationX(v.getTranslationX()+dx);
                        v.setTranslationY(v.getTranslationY()+dy);
                        x[0]=nx;y[0]=ny;
                    }return true;
                case MotionEvent.ACTION_POINTER_UP:scale[0]=false;return true;
                case MotionEvent.ACTION_UP:
                    scale[0]=false;dist[0]=0;
                    if(!moved[0])v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    scale[0]=false;dist[0]=0;return true;
            }
            return true;
        });
    }

    private byte[] renderCapturedComposition(View v){
        try{
            Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(b));
            ByteArrayOutputStream o=new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG,95,o);
            return o.toByteArray();
        }catch(Exception e){return null;}
    }

    private Typeface previewTextTypeface(int styleIndex){
        switch(styleIndex){
            case 1: return Typeface.create(Typeface.SERIF,Typeface.NORMAL);
            case 2: return Typeface.create("cursive",Typeface.ITALIC);
            case 3: return Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);
            case 4: return Typeface.create("sans-serif-black",Typeface.BOLD);
            case 5: return Typeface.create("sans-serif-condensed",Typeface.NORMAL);
            default: return Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD);
        }
    }

    private float previewTextSize(int styleIndex){
        switch(styleIndex){
            case 2:return 38f;
            case 4:return 38f;
            case 5:return 34f;
            default:return 36f;
        }
    }

    private void applyPreviewTextStyle(EditText input,int styleIndex,int textColor,boolean boxed,int align){
        input.setTypeface(previewTextTypeface(styleIndex));
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,previewTextSize(styleIndex));
        input.setTextColor(textColor);
        if(boxed){
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(Color.argb(175,0,0,0));
            bg.setCornerRadius(dp(18));
            input.setBackground(bg);
            input.setPadding(dp(16),dp(10),dp(16),dp(10));
        }else{
            input.setBackgroundColor(Color.TRANSPARENT);
            input.setPadding(dp(0),dp(0),dp(0),dp(0));
        }
        if(align==0){
            input.setGravity(Gravity.CENTER);
            input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }else if(align==1){
            input.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }else{
            input.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        }
    }

    private TextView buildTextStyleChip(String label){
        TextView chip=text(label,14,Color.WHITE,Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(18),0,dp(18),0);
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(dp(15));
        g.setStroke(dp(1),Color.argb(70,255,255,255));
        chip.setBackground(g);
        return chip;
    }

    private void showCapturedTextEditor(final FrameLayout composition,final Runnable backDestination){
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final FrameLayout page=new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);

        Bitmap baseBmp=Bitmap.createBitmap(composition.getWidth(),composition.getHeight(),Bitmap.Config.ARGB_8888);
        composition.draw(new Canvas(baseBmp));

        final int headerH=dp(54);
        final int controlsH=dp(156);

        ImageView bg=new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageBitmap(baseBmp);
        page.addView(bg,new FrameLayout.LayoutParams(-1,-1));

        View scrim=new View(this);
        scrim.setBackgroundColor(Color.argb(85,0,0,0));
        page.addView(scrim,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        header.setPadding(dp(14),0,dp(14),0);
        header.setBackgroundColor(Color.argb(28,0,0,0));
        TextView done=text("Done",18,Color.WHITE,Typeface.BOLD);
        done.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(done,new LinearLayout.LayoutParams(-2,-1));
        page.addView(header,new FrameLayout.LayoutParams(-1,headerH,Gravity.TOP));

        final EditText input=new EditText(this);
        input.setHint("Type a message...");
        input.setHintTextColor(Color.argb(175,255,255,255));
        input.setTextColor(Color.WHITE);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,36);
        input.setMaxLines(6);
        input.setMinLines(1);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(false);
        if(previewTextPrefill!=null&&!previewTextPrefill.isEmpty()){
            input.setText(previewTextPrefill);
            input.setSelection(input.length());
            previewTextPrefill="";
        }
        FrameLayout.LayoutParams ilp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        ilp.leftMargin=dp(46);
        ilp.rightMargin=dp(46);
        page.addView(input,ilp);

        final View sliderTrack=new View(this);
        GradientDrawable sliderBg=new GradientDrawable();
        sliderBg.setColor(Color.argb(210,255,255,255));
        sliderBg.setCornerRadius(dp(4));
        sliderTrack.setBackground(sliderBg);
        FrameLayout.LayoutParams trackLp=new FrameLayout.LayoutParams(dp(4),dp(300),Gravity.START|Gravity.CENTER_VERTICAL);
        trackLp.leftMargin=dp(25);
        page.addView(sliderTrack,trackLp);

        final View sliderKnob=new View(this);
        GradientDrawable knobBg=new GradientDrawable();
        knobBg.setShape(GradientDrawable.OVAL);
        knobBg.setColor(Color.WHITE);
        sliderKnob.setBackground(knobBg);
        FrameLayout.LayoutParams knobLp=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.START|Gravity.CENTER_VERTICAL);
        knobLp.leftMargin=dp(15);
        page.addView(sliderKnob,knobLp);

        final int[] styleIndex={0};
        final int[] textColor={Color.WHITE};
        final boolean[] boxed={false};
        final int[] align={0};
        final float[] textSize={36f};
        final boolean[] paletteVisible={false};

        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams blp=new FrameLayout.LayoutParams(-1,controlsH,Gravity.BOTTOM);
        page.addView(bottom,blp);

        final HorizontalScrollView colorsScroll=new HorizontalScrollView(this);
        colorsScroll.setHorizontalScrollBarEnabled(false);
        colorsScroll.setVisibility(View.GONE);
        LinearLayout colorRow=new LinearLayout(this);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(dp(8),0,dp(8),0);
        colorsScroll.addView(colorRow,new HorizontalScrollView.LayoutParams(-2,dp(50)));
        bottom.addView(colorsScroll,new LinearLayout.LayoutParams(-1,dp(50)));

        int[] palette=new int[]{
            Color.WHITE,Color.BLACK,Color.rgb(176,42,211),Color.rgb(117,77,255),Color.rgb(108,71,241),
            Color.rgb(194,225,255),Color.rgb(69,198,24),Color.rgb(176,221,49),Color.rgb(198,233,70),
            Color.rgb(255,73,120),Color.rgb(255,147,41),Color.rgb(255,214,51),Color.rgb(0,189,255),
            Color.rgb(52,235,183),Color.rgb(255,0,0),Color.rgb(255,255,255)
        };
        final java.util.ArrayList<View> colorDots=new java.util.ArrayList<>();
        for(int c:palette){
            View dot=new View(this);
            dot.setTag(c);
            styleColorSwatch(dot,c,c==textColor[0]);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(38),dp(38));
            lp.leftMargin=dp(6);lp.rightMargin=dp(6);
            colorRow.addView(dot,lp);
            colorDots.add(dot);
            dot.setOnClickListener(v->{textColor[0]=c;for(View swatch:colorDots)styleColorSwatch(swatch,(Integer)swatch.getTag(),swatch==v);applyPreviewTextStyle(input,styleIndex[0],textColor[0],boxed[0],align[0]);});
        }

        final HorizontalScrollView stylesScroll=new HorizontalScrollView(this);
        stylesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout styleRow=new LinearLayout(this);
        styleRow.setGravity(Gravity.CENTER_VERTICAL);
        styleRow.setPadding(dp(10),0,dp(10),0);
        stylesScroll.addView(styleRow,new HorizontalScrollView.LayoutParams(-2,-1));
        LinearLayout.LayoutParams styleLp=new LinearLayout.LayoutParams(-1,dp(52));
        styleLp.topMargin=dp(6);
        bottom.addView(stylesScroll,styleLp);

        final String[] styles={"Modern","Classic","Signature","Editor","Poster","Deco"};
        final java.util.ArrayList<TextView> chips=new java.util.ArrayList<>();
        for(int i=0;i<styles.length;i++){
            TextView chip=buildTextStyleChip(styles[i]);
            chip.setTypeface(previewTextTypeface(i));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(42));
            cp.leftMargin=dp(4);cp.rightMargin=dp(4);
            styleRow.addView(chip,cp);
            final int idx=i;
            chip.setOnClickListener(v->{
                styleIndex[0]=idx;
                for(int j=0;j<chips.size();j++){
                    TextView t=chips.get(j);
                    GradientDrawable gg=(GradientDrawable)t.getBackground();
                    if(j==idx){gg.setColor(Color.WHITE);t.setTextColor(Color.rgb(38,38,38));t.setTypeface(previewTextTypeface(j));}
                    else{gg.setColor(Color.TRANSPARENT);t.setTextColor(Color.WHITE);t.setTypeface(previewTextTypeface(j));}
                }
                applyPreviewTextStyle(input,styleIndex[0],textColor[0],boxed[0],align[0]);
            });
            chips.add(chip);
        }
        if(!chips.isEmpty()) chips.get(0).performClick();

        LinearLayout controlWrap=new LinearLayout(this);
        controlWrap.setGravity(Gravity.CENTER);
        bottom.addView(controlWrap,new LinearLayout.LayoutParams(-1,dp(54)));

        LinearLayout controls=new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(10),dp(6),dp(10),dp(6));
        controls.setBackground(bg(Color.rgb(58,52,66),16));
        controlWrap.addView(controls,new LinearLayout.LayoutParams(-2,-2));

        TextView ctrlAa=text("Aa",20,Color.WHITE,Typeface.NORMAL);ctrlAa.setGravity(Gravity.CENTER);
        ctrlAa.setBackground(bg(Color.rgb(98,92,106),12));
        controls.addView(ctrlAa,new LinearLayout.LayoutParams(dp(72),dp(42)));

        TextView ctrlColor=text("",18,Color.WHITE,Typeface.NORMAL);ctrlColor.setGravity(Gravity.CENTER);
        GradientDrawable colorBg=new GradientDrawable(GradientDrawable.Orientation.BL_TR,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA});
        colorBg.setCornerRadius(dp(15));
        ctrlColor.setBackground(colorBg);
        LinearLayout.LayoutParams c1=new LinearLayout.LayoutParams(dp(30),dp(30));c1.leftMargin=dp(10);c1.rightMargin=dp(6);
        controls.addView(ctrlColor,c1);

        TextView ctrlAlign=text("☰",22,Color.WHITE,Typeface.NORMAL);ctrlAlign.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams c2=new LinearLayout.LayoutParams(dp(50),dp(42));c2.leftMargin=dp(8);
        controls.addView(ctrlAlign,c2);

        TextView ctrlBox=text("A",22,Color.rgb(38,38,38),Typeface.BOLD);ctrlBox.setGravity(Gravity.CENTER);
        GradientDrawable boxBg=new GradientDrawable();boxBg.setColor(Color.WHITE);boxBg.setCornerRadius(dp(8));
        ctrlBox.setBackground(boxBg);
        LinearLayout.LayoutParams c3=new LinearLayout.LayoutParams(dp(42),dp(42));c3.leftMargin=dp(8);
        controls.addView(ctrlBox,c3);

        ctrlColor.setOnClickListener(v->{
            paletteVisible[0]=!paletteVisible[0];
            colorsScroll.setVisibility(paletteVisible[0]?View.VISIBLE:View.GONE);
        });
        ctrlAlign.setOnClickListener(v->{
            align[0]=(align[0]+1)%3;
            applyPreviewTextStyle(input,styleIndex[0],textColor[0],boxed[0],align[0]);
        });
        ctrlBox.setOnClickListener(v->{
            boxed[0]=!boxed[0];
            applyPreviewTextStyle(input,styleIndex[0],textColor[0],boxed[0],align[0]);
        });
        ctrlAa.setOnClickListener(v->input.requestFocus());

        applyPreviewTextStyle(input,styleIndex[0],textColor[0],boxed[0],align[0]);

        sliderKnob.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE){
                float center=page.getHeight()/2f;
                float min=center-dp(150),max=center+dp(150);
                float yy=Math.max(min,Math.min(max,e.getRawY()));
                v.setTranslationY(yy-center);
                float progress=(yy-min)/Math.max(1f,max-min);
                textSize[0]=52f-(progress*34f);
                input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,textSize[0]);
                return true;
            }
            return true;
        });

        done.setOnClickListener(v->{
            String value=input.getText().toString().trim();
            if(value.isEmpty()){
                d.dismiss();
                return;
            }

            final TextView placed=text(
                value,
                input.getTextSize()/getResources().getDisplayMetrics().scaledDensity,
                textColor[0],
                Typeface.BOLD
            );
            placed.setTextColor(textColor[0]);
            placed.setTypeface(previewTextTypeface(styleIndex[0]));
            placed.setGravity(input.getGravity());
            placed.setPadding(dp(8),dp(4),dp(8),dp(4));
            placed.setShadowLayer(dp(3),0,dp(1),Color.argb(170,0,0,0));

            if(boxed[0]){
                placed.setBackground(bg(Color.argb(175,0,0,0),18));
            }else{
                placed.setBackgroundColor(Color.TRANSPARENT);
            }

            composition.addView(
                placed,
                new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER)
            );

            placed.setOnClickListener(clickedView->{
                previewTextPrefill=placed.getText().toString();
                composition.removeView(placed);
                showCapturedTextEditor(composition,backDestination);
            });
            makePreviewTransformable(placed);

            d.dismiss();
        });

        d.setContentView(page);
        d.show();
        Window w=d.getWindow();
        if(w!=null){
            w.setLayout(-1,-1);
            w.setStatusBarColor(Color.BLACK);
            w.setNavigationBarColor(Color.BLACK);
        }
        input.requestFocus();
        input.postDelayed(()->{
            try{
                android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm!=null) imm.showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }catch(Exception ignore){}
        },120);
    }

    private void showPreviewStickerPicker(final FrameLayout composition){
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(38,38,38));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton close=icon(R.drawable.ic_msg_close,42,Color.WHITE);close.setBackgroundColor(Color.TRANSPARENT);
        TextView title=text("Stickers",18,Color.WHITE,Typeface.BOLD);title.setGravity(Gravity.CENTER);
        top.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        top.addView(new Space(this),new LinearLayout.LayoutParams(dp(48),dp(48)));
        page.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout searchWrap=new LinearLayout(this);searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(dp(11),0,dp(10),0);searchWrap.setBackground(bg(Color.rgb(54,54,54),18));
        ImageView si=new ImageView(this);si.setImageResource(R.drawable.ic_sticker_search);si.setColorFilter(Color.rgb(159,167,178));
        searchWrap.addView(si,new LinearLayout.LayoutParams(dp(23),dp(23)));
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Search stickers");
        search.setHintTextColor(Color.rgb(159,167,178));search.setTextColor(Color.WHITE);
        search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(7),0,0,0);
        searchWrap.addView(search,new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(42));
        slp.leftMargin=dp(12);slp.rightMargin=dp(12);slp.bottomMargin=dp(8);page.addView(searchWrap,slp);

        FrameLayout body=new FrameLayout(this);ScrollView scroll=new ScrollView(this);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(3);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));ProgressBar loading=new ProgressBar(this);
        body.addView(loading,new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER));
        page.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        final int[] rid={0};
        class Loader{void load(String query){
            int mine=++rid[0];loading.setVisibility(View.VISIBLE);grid.removeAllViews();
            String q=query==null?"":query.trim();
            String url=q.isEmpty()
                ?"https://api.giphy.com/v1/stickers/trending?api_key="+Uri.encode(COMMENT_STICKER_API_KEY)+"&limit=50&rating=g"
                :"https://api.giphy.com/v1/stickers/search?api_key="+Uri.encode(COMMENT_STICKER_API_KEY)+"&q="+Uri.encode(q)+"&limit=50&offset=0&rating=g&lang=en";
            api.get(url,(json,error)->main.post(()->{
                if(mine!=rid[0])return;loading.setVisibility(View.GONE);
                JSONArray ar=json.optJSONArray("data");if(error!=null||ar==null)return;
                int cell=getResources().getDisplayMetrics().widthPixels/3;
                for(int i=0;i<ar.length();i++){
                    JSONObject item=ar.optJSONObject(i);if(item==null)continue;
                    JSONObject imgs=item.optJSONObject("images");if(imgs==null)continue;
                    JSONObject fixed=imgs.optJSONObject("fixed_width");if(fixed==null)fixed=imgs.optJSONObject("original");
                    if(fixed==null)continue;String u=fixed.optString("url","");if(u.isEmpty())continue;
                    ImageView iv=new ImageView(MainActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=cell;lp.height=cell;grid.addView(iv,lp);
                    stickers.load(u,iv);final String su=u;
                    iv.setOnClickListener(v->{
                        ImageView placed=new ImageView(MainActivity.this);placed.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        composition.addView(placed,new FrameLayout.LayoutParams(dp(120),dp(120),Gravity.CENTER));
                        stickers.load(su,placed);makePreviewTransformable(placed);d.dismiss();
                    });
                }
            }));
        }}
        Loader loader=new Loader();loader.load("");
        final Runnable[] pending={null};
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence cs,int st,int b,int c){
                if(pending[0]!=null)main.removeCallbacks(pending[0]);String q=cs==null?"":cs.toString();
                pending[0]=()->loader.load(q);main.postDelayed(pending[0],280);
            }
            public void afterTextChanged(Editable e){}
        });
        close.setOnClickListener(v->d.dismiss());d.setContentView(page);d.show();
        Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
    }

    private void showCapturedDrawingEditor(
        final FrameLayout composition,
        final Runnable backDestination
    ){
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final FrameLayout page=new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);

        Bitmap baseBmp=Bitmap.createBitmap(composition.getWidth(),composition.getHeight(),Bitmap.Config.ARGB_8888);
        composition.draw(new Canvas(baseBmp));

        final int headerH=dp(50);
        final int bottomToolsH=dp(118);

        ImageView base=new ImageView(this);
        base.setScaleType(ImageView.ScaleType.FIT_CENTER);
        base.setImageBitmap(baseBmp);
        FrameLayout.LayoutParams blp=new FrameLayout.LayoutParams(-1,-1);
        blp.topMargin=headerH;
        blp.bottomMargin=bottomToolsH;
        page.addView(base,blp);

        DrawOverlay drawView=new DrawOverlay(this);
        drawView.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(-1,-1);
        dlp.topMargin=headerH;
        dlp.bottomMargin=bottomToolsH;
        page.addView(drawView,dlp);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12),0,dp(12),0);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(135,0,190),Color.rgb(199,0,213)}));

        TextView undo=text("Undo",17,Color.WHITE,Typeface.BOLD);
        undo.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(undo,new LinearLayout.LayoutParams(dp(70),-1));

        LinearLayout tools=new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);

        ImageButton penBrush=icon(R.drawable.ic_draw_pen_brush_ref,36,Color.WHITE);penBrush.clearColorFilter();penBrush.setBackgroundColor(Color.TRANSPARENT);
        ImageButton penHi=icon(R.drawable.ic_draw_pen_spray_ref,36,Color.WHITE);penHi.clearColorFilter();penHi.setBackgroundColor(Color.TRANSPARENT);
        ImageButton penMarker=icon(R.drawable.ic_draw_pen_marker_ref,36,Color.WHITE);penMarker.clearColorFilter();penMarker.setBackgroundColor(Color.TRANSPARENT);
        ImageButton penErase=icon(R.drawable.ic_draw_pen_eraser_ref,36,Color.WHITE);penErase.clearColorFilter();penErase.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout.LayoutParams ib=new LinearLayout.LayoutParams(dp(38),dp(38));
        tools.addView(penBrush,ib);
        LinearLayout.LayoutParams ib2=new LinearLayout.LayoutParams(dp(38),dp(38));ib2.leftMargin=dp(12);tools.addView(penHi,ib2);
        LinearLayout.LayoutParams ib3=new LinearLayout.LayoutParams(dp(38),dp(38));ib3.leftMargin=dp(12);tools.addView(penMarker,ib3);
        LinearLayout.LayoutParams ib4=new LinearLayout.LayoutParams(dp(38),dp(38));ib4.leftMargin=dp(12);tools.addView(penErase,ib4);
        header.addView(tools,new LinearLayout.LayoutParams(0,-1,1));

        TextView done=text("Done",17,Color.WHITE,Typeface.BOLD);
        done.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        header.addView(done,new LinearLayout.LayoutParams(dp(70),-1));
        page.addView(header,new FrameLayout.LayoutParams(-1,headerH,Gravity.TOP));

        Runnable refreshToolState=()->{
            penBrush.setBackground(drawView.getBrushStyle()==0?bg(Color.argb(70,255,255,255),19):null);
            penHi.setBackground(drawView.getBrushStyle()==1?bg(Color.argb(70,255,255,255),19):null);
            penMarker.setBackground(drawView.getBrushStyle()==2?bg(Color.argb(70,255,255,255),19):null);
            penErase.setBackground(drawView.getBrushStyle()==3?bg(Color.argb(70,255,255,255),19):null);
        };
        penBrush.setOnClickListener(v->{drawView.setBrushStyle(0);refreshToolState.run();});
        penHi.setOnClickListener(v->{drawView.setBrushStyle(1);refreshToolState.run();});
        penMarker.setOnClickListener(v->{drawView.setBrushStyle(2);refreshToolState.run();});
        penErase.setOnClickListener(v->{drawView.setBrushStyle(3);refreshToolState.run();});
        drawView.setBrushStyle(0);refreshToolState.run();

        undo.setOnClickListener(v->drawView.undo());

        done.setOnClickListener(v->{
            Bitmap strokes=drawView.exportBitmap();
            if(strokes!=null){
                ImageView layer=new ImageView(this);
                layer.setScaleType(ImageView.ScaleType.FIT_XY);
                layer.setImageBitmap(strokes);
                composition.addView(layer,new FrameLayout.LayoutParams(-1,-1));
            }
            d.dismiss();
        });

        HorizontalScrollView colorsScroll=new HorizontalScrollView(this);
        colorsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colors=new LinearLayout(this);
        colors.setGravity(Gravity.CENTER_VERTICAL);
        colors.setPadding(dp(8),dp(8),dp(8),dp(8));
        colorsScroll.addView(colors,new HorizontalScrollView.LayoutParams(-2,-1));

        int[] palette={
            Color.WHITE,Color.BLACK,0xff8e8e93,0xffaeaeb2,
            0xffbf5af2,0xffaf52de,0xffff2d55,0xffff375f,0xffff453a,
            0xffff6961,0xffff7a00,0xffff9f0a,0xffffd60a,0xffffe66d,
            0xffaeea00,0xff32d74b,0xff30d158,0xff00c7be,0xff64d2ff,
            0xff0a84ff,0xff007aff,0xff5e5ce6,0xff5856d6,0xffc7d8ff
        };
        final java.util.ArrayList<View> drawColorDots=new java.util.ArrayList<>();
        for(int color:palette){
            View dot=new View(this);
            dot.setTag(color);
            styleColorSwatch(dot,color,color==Color.WHITE);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(34),dp(34));
            lp.leftMargin=dp(6);lp.rightMargin=dp(6);
            colors.addView(dot,lp);
            drawColorDots.add(dot);
            dot.setOnClickListener(v->{drawView.setDrawColor(color);for(View swatch:drawColorDots)styleColorSwatch(swatch,(Integer)swatch.getTag(),swatch==v);});
        }

        FrameLayout.LayoutParams colorsLp=new FrameLayout.LayoutParams(-1,dp(54),Gravity.BOTTOM);
        colorsLp.leftMargin=dp(52);colorsLp.rightMargin=dp(8);colorsLp.bottomMargin=dp(24);
        page.addView(colorsScroll,colorsLp);

        FrameLayout sliderTouch=new FrameLayout(this);
        sliderTouch.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams sliderLp=
            new FrameLayout.LayoutParams(dp(56),dp(306),Gravity.START|Gravity.CENTER_VERTICAL);
        page.addView(sliderTouch,sliderLp);

        View track=new View(this);
        GradientDrawable trackBg=new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.WHITE,Color.WHITE,Color.rgb(180,180,180),Color.rgb(105,105,105)}
        );
        trackBg.setCornerRadius(dp(4));
        track.setBackground(trackBg);
        sliderTouch.addView(
            track,
            new FrameLayout.LayoutParams(dp(5),dp(286),Gravity.CENTER)
        );

        View knob=new View(this);
        GradientDrawable knobBg=new GradientDrawable();
        knobBg.setShape(GradientDrawable.OVAL);
        knobBg.setColor(Color.WHITE);
        knob.setBackground(knobBg);
        sliderTouch.addView(
            knob,
            new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER)
        );

        View.OnTouchListener sliderListener=(v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN||
               e.getActionMasked()==MotionEvent.ACTION_MOVE){
                float center=sliderTouch.getHeight()/2f;
                float min=center-dp(143),max=center+dp(143);
                float yy=Math.max(min,Math.min(max,e.getY()));
                knob.setTranslationY(yy-center);
                float progress=(yy-min)/Math.max(1f,max-min);
                float width=dp(20)-(progress*dp(17));
                drawView.setStrokeWidth(Math.max(dp(2),width));
                return true;
            }
            return true;
        };
        sliderTouch.setOnTouchListener(sliderListener);
        knob.setOnTouchListener(sliderListener);

        d.setContentView(page);
        d.show();
        Window w=d.getWindow();
        if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}    
    }

    private void showDiscardPhotoConfirmation(final Dialog preview,final Runnable discardAction){final Dialog confirm=new Dialog(this);confirm.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.argb(100,0,0,0));LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_HORIZONTAL);card.setBackground(bg(Color.rgb(30,35,39),16));FrameLayout.LayoutParams cardLp=new FrameLayout.LayoutParams(dp(270),-2,Gravity.CENTER);overlay.addView(card,cardLp);TextView title=text("Discard photo?",18,Color.WHITE,Typeface.BOLD);title.setGravity(Gravity.CENTER);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,dp(44));titleLp.topMargin=dp(15);card.addView(title,titleLp);TextView body=text("If you go back now, you\nwill lose your photo.",16,Color.WHITE,Typeface.NORMAL);body.setGravity(Gravity.CENTER);body.setLineSpacing(0,1.03f);LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,dp(72));bodyLp.leftMargin=dp(15);bodyLp.rightMargin=dp(15);bodyLp.bottomMargin=dp(5);card.addView(body,bodyLp);View firstLine=new View(this);firstLine.setBackgroundColor(Color.rgb(49,54,58));card.addView(firstLine,new LinearLayout.LayoutParams(-1,dp(1)));TextView discard=text("Discard",17,Color.rgb(255,38,58),Typeface.BOLD);discard.setGravity(Gravity.CENTER);card.addView(discard,new LinearLayout.LayoutParams(-1,dp(52)));View secondLine=new View(this);secondLine.setBackgroundColor(Color.rgb(49,54,58));card.addView(secondLine,new LinearLayout.LayoutParams(-1,dp(1)));TextView keep=text("Keep editing",17,Color.WHITE,Typeface.NORMAL);keep.setGravity(Gravity.CENTER);card.addView(keep,new LinearLayout.LayoutParams(-1,dp(52)));discard.setOnClickListener(v->{confirm.dismiss();if(discardAction!=null)discardAction.run();});keep.setOnClickListener(v->confirm.dismiss());overlay.setOnClickListener(v->{if(v==overlay)confirm.dismiss();});confirm.setContentView(overlay);confirm.show();Window window=confirm.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setDimAmount(0);window.setLayout(-1,-1);}}

    private void showCapturedMediaPreview(final byte[] bytes,final Runnable backDestination){
        if(bytes==null||bytes.length==0)return;
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout page=new FrameLayout(this);page.setBackgroundColor(Color.BLACK);
        FrameLayout composition=new FrameLayout(this);composition.setBackgroundColor(Color.BLACK);composition.setClipChildren(false);
        ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);photo.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));
        composition.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams cp0=new FrameLayout.LayoutParams(-1,-1);cp0.topMargin=dp(46);cp0.bottomMargin=dp(110);page.addView(composition,cp0);

        ImageButton back=icon(R.drawable.ic_camera_back_ref,46,Color.WHITE);back.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.TOP|Gravity.START);bp.leftMargin=dp(10);bp.topMargin=dp(58);page.addView(back,bp);
        Runnable requestDiscard=()->showDiscardPhotoConfirmation(d,()->{if(backDestination!=null)backDestination.run();main.postDelayed(d::dismiss,140);});
        back.setOnClickListener(v->requestDiscard.run());

        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.VERTICAL);tools.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView aa=text("Aa",20,Color.WHITE,Typeface.NORMAL);aa.setGravity(Gravity.CENTER);aa.setBackground(bg(Color.rgb(36,37,41),20));
        tools.addView(aa,new LinearLayout.LayoutParams(dp(40),dp(40)));aa.setOnClickListener(v->showCapturedTextEditor(composition,backDestination));

        ImageButton sticker=icon(R.drawable.ic_camera_sticker_exact_ref,40,Color.WHITE);sticker.clearColorFilter();sticker.setPadding(dp(8),dp(8),dp(8),dp(8));
        sticker.setBackground(bg(Color.rgb(36,37,41),20));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(40),dp(40));sp.topMargin=dp(6);tools.addView(sticker,sp);
        sticker.setOnClickListener(v->showPreviewStickerPicker(composition));

        ImageButton draw=icon(R.drawable.ic_camera_draw_exact_ref,40,Color.WHITE);draw.clearColorFilter();draw.setPadding(dp(8),dp(8),dp(8),dp(8));
        draw.setBackground(bg(Color.rgb(36,37,41),20));LinearLayout.LayoutParams dp0=new LinearLayout.LayoutParams(dp(40),dp(40));dp0.topMargin=dp(6);tools.addView(draw,dp0);
        draw.setOnClickListener(v->showCapturedDrawingEditor(composition,backDestination));

        ImageButton download=icon(R.drawable.ic_camera_download_ref,40,Color.WHITE);download.setPadding(dp(8),dp(8),dp(8),dp(8));
        download.setBackground(bg(Color.rgb(36,37,41),20));LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(dp(40),dp(40));xp.topMargin=dp(6);tools.addView(download,xp);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(dp(46),-2,Gravity.TOP|Gravity.END);tp.rightMargin=dp(10);tp.topMargin=dp(66);page.addView(tools,tp);

        download.setOnClickListener(v->{try{
            byte[] rendered=renderCapturedComposition(composition);if(rendered==null)return;
            android.provider.MediaStore.Images.Media.insertImage(getContentResolver(),BitmapFactory.decodeByteArray(rendered,0,rendered.length),
                "Messenger-"+System.currentTimeMillis(),"Messenger photo");toast("Saved");
        }catch(Exception e){toast("Couldn't save photo.");}});

        int[] mode={2};TextView view=text("View twice",14,Color.WHITE,Typeface.BOLD);view.setGravity(Gravity.CENTER_VERTICAL);setCapturedViewModeButton(view,2);
        view.setPadding(dp(31),0,dp(12),0);view.setCompoundDrawablePadding(dp(7));
        view.setBackground(bg(Color.rgb(34,35,39),22));FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(dp(166),dp(46),Gravity.BOTTOM|Gravity.START);
        vp.leftMargin=dp(14);vp.bottomMargin=dp(18);page.addView(view,vp);view.setOnClickListener(v->showCapturedViewModeMenu(view,mode));

        LinearLayout send=new LinearLayout(this);send.setGravity(Gravity.CENTER);send.setPadding(dp(6),0,dp(11),0);send.setBackground(bg(Color.WHITE,23));
        View avatar=buildUserAvatar(activeConversation==null?"":activeConversation.optString("avatar",""),conversationName(),30);
        send.addView(avatar,new LinearLayout.LayoutParams(dp(30),dp(30)));TextView st=text("Send",15,Color.rgb(35,35,35),Typeface.BOLD);st.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-2,dp(40));stp.leftMargin=dp(6);send.addView(st,stp);
        FrameLayout.LayoutParams sendp=new FrameLayout.LayoutParams(dp(112),dp(46),Gravity.BOTTOM|Gravity.END);sendp.rightMargin=dp(14);sendp.bottomMargin=dp(18);page.addView(send,sendp);
        final boolean[] captureSending={false};
        send.setOnClickListener(v->{
            if(captureSending[0])return;
            byte[] rendered=renderCapturedComposition(composition);
            if(rendered==null)return;
            captureSending[0]=true;
            send.setEnabled(false);
            d.dismiss();
            uploadAttachment(
                rendered,
                "camera-"+System.currentTimeMillis()+".jpg",
                "image/jpeg",
                mode[0]
            );
        });

        d.setOnKeyListener((dialog,keyCode,event)->{if(keyCode==android.view.KeyEvent.KEYCODE_BACK&&event.getAction()==android.view.KeyEvent.ACTION_UP){requestDiscard.run();return true;}return false;});
        d.setContentView(page);d.show();Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
    }

    private void showCapturedViewModeMenu(
        final TextView anchor,
        final int[] mode
    ){
        final android.widget.PopupWindow popup=new android.widget.PopupWindow(this);
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20),dp(16),dp(20),dp(16));
        card.setBackground(bg(Color.rgb(40,42,48),18));

        TextView help=text(
            "Set how many times this\\nphoto can be viewed.",
            14,
            Color.rgb(178,183,194),
            Typeface.NORMAL
        );
        card.addView(help,new LinearLayout.LayoutParams(dp(270),dp(56)));

        String[] labels={"View once","View twice","Unlimited views"};
        int[] values={1,2,0};

        for(int i=0;i<labels.length;i++){
            final int value=values[i];
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            ImageView icon=new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageResource(
                value==1
                    ?R.drawable.ic_camera_view_once_ref
                    :value==2
                        ?R.drawable.ic_camera_view_twice_ref
                        :R.drawable.ic_camera_view_unlimited_ref
            );
            icon.setPadding(dp(value==0?7:10),dp(value==0?7:10),dp(value==0?7:10),dp(value==0?7:10));
            icon.setTranslationX(value==0?dp(5):0);
            icon.setTranslationY(dp(2));
            row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(62)));

            TextView label=text(labels[i],18,Color.WHITE,Typeface.NORMAL);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(62),1));

            ImageView check=new ImageView(this);
            check.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            if(mode[0]==value)check.setImageResource(R.drawable.ic_camera_view_unlimited_ref);
            check.setPadding(dp(6),dp(6),dp(6),dp(6));
            row.addView(check,new LinearLayout.LayoutParams(dp(32),dp(62)));

            card.addView(row,new LinearLayout.LayoutParams(dp(280),dp(62)));

            row.setOnClickListener(v->{
                mode[0]=value;
                setCapturedViewModeButton(anchor,value);
                popup.dismiss();
            });
        }

        popup.setContentView(card);
        popup.setWidth(dp(320));
        popup.setHeight(-2);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        );
        popup.setElevation(dp(12));
        popup.showAsDropDown(anchor,0,-dp(330));
    }

    private void setCapturedViewModeButton(TextView button,int value){
        if(button==null)return;
        if(value==0&&"media-picker-mode-button".equals(button.getTag())){button.setCompoundDrawables(null,null,null,null);button.setCompoundDrawablePadding(0);button.setText("Unlimited views");return;}
        int drawable=value==1
            ?R.drawable.ic_camera_view_once_ref
            :value==2
                ?R.drawable.ic_camera_view_twice_ref
                :R.drawable.ic_camera_view_unlimited_ref;
        android.graphics.drawable.Drawable icon=getDrawable(drawable);
        if(value==0&&icon!=null)icon=new android.graphics.drawable.InsetDrawable(icon,dp(3),dp(1),0,0);
        if(icon!=null)icon.setBounds(0,0,dp(value==0?23:19),dp(value==0?23:19));
        button.setCompoundDrawables(icon,null,null,null);
        button.setCompoundDrawablePadding(dp(6));
        button.setText(value==1?"View once":value==2?"View twice":"Unlimited");
    }

    private void showMessageTextCreate(){
        showMessageTextCreate(null);
    }

    private void showMessageTextCreate(final byte[] baseBytes){
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final FrameLayout page=new FrameLayout(this);
        final FrameLayout editCanvas=new FrameLayout(this);

        final int[][] palettes={
            {0xffff315f,0xffff7a18,0xffffd138},
            {0xff833ab4,0xfffd1d1d,0xfffcb045},
            {0xff5b46f6,0xffcd30fa,0xffff508d}
        };
        final int[] ix={0};

        Runnable paint=()->{
            if(baseBytes==null){
                editCanvas.setBackground(
                    new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        palettes[ix[0]]
                    )
                );
            }
        };
        paint.run();

        FrameLayout.LayoutParams editLp=new FrameLayout.LayoutParams(-1,-1);
        editLp.topMargin=dp(38);
        editLp.bottomMargin=dp(92);
        page.addView(editCanvas,editLp);

        if(baseBytes!=null){
            ImageView base=new ImageView(this);
            base.setScaleType(ImageView.ScaleType.CENTER_CROP);
            base.setImageBitmap(BitmapFactory.decodeByteArray(baseBytes,0,baseBytes.length));
            editCanvas.addView(base,new FrameLayout.LayoutParams(-1,-1));
        }

        View shade=new View(this);
        shade.setBackgroundColor(Color.argb(55,0,0,0));
        page.addView(shade,new FrameLayout.LayoutParams(-1,dp(38),Gravity.TOP));

        LinearLayout header=messageTitle();
        for(int i=0;i<header.getChildCount();i++){
            View child=header.getChildAt(i);
            if(child instanceof TextView)((TextView)child).setTextSize(15);
        }
        page.addView(header,new FrameLayout.LayoutParams(-1,dp(38),Gravity.TOP));

        ImageButton close=icon(R.drawable.ic_msg_close,40,Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams cp=
            new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP|Gravity.START);
        cp.leftMargin=dp(10);
        cp.topMargin=dp(44);
        page.addView(close,cp);
        close.setOnClickListener(v->{
            d.dismiss();
            if(baseBytes!=null)showCapturedMediaPreview(baseBytes,this::showMessageCamera);
        });

        EditText input=new EditText(this);
        input.setHint("Type a message...");
        input.setHintTextColor(Color.argb(130,255,255,255));
        input.setTextColor(Color.WHITE);
        input.setTextSize(29);
        input.setGravity(Gravity.CENTER);
        input.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams ip=
            new FrameLayout.LayoutParams(-1,dp(190),Gravity.CENTER);
        ip.leftMargin=dp(28);
        ip.rightMargin=dp(28);
        editCanvas.addView(input,ip);

        FrameLayout createWrap=new FrameLayout(this);
        GradientDrawable createOuter=new GradientDrawable();
        createOuter.setShape(GradientDrawable.OVAL);
        createOuter.setColor(Color.TRANSPARENT);
        createOuter.setStroke(dp(3),Color.WHITE);
        createWrap.setBackground(createOuter);

        FrameLayout.LayoutParams createWrapLp=
            new FrameLayout.LayoutParams(dp(76),dp(76),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        createWrapLp.bottomMargin=dp(56);
        page.addView(createWrap,createWrapLp);

        TextView create=text("Aa",33,Color.BLACK,Typeface.NORMAL);
        create.setGravity(Gravity.CENTER);
        create.setBackground(bg(Color.WHITE,32));
        FrameLayout.LayoutParams createLp=
            new FrameLayout.LayoutParams(dp(64),dp(64),Gravity.CENTER);
        createWrap.addView(create,createLp);

        createWrap.setOnClickListener(v->{
            try{
                input.clearFocus();
                InputMethodManager imm=
                    (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm!=null)imm.hideSoftInputFromWindow(input.getWindowToken(),0);

                editCanvas.post(()->{
                    try{
                        if(editCanvas.getWidth()<=0||editCanvas.getHeight()<=0)return;
                        Bitmap rendered=Bitmap.createBitmap(
                            editCanvas.getWidth(),
                            editCanvas.getHeight(),
                            Bitmap.Config.ARGB_8888
                        );
                        Canvas canvas=new Canvas(rendered);
                        editCanvas.draw(canvas);

                        java.io.ByteArrayOutputStream out=
                            new java.io.ByteArrayOutputStream();
                        rendered.compress(Bitmap.CompressFormat.JPEG,95,out);

                        d.dismiss();
                        showCapturedMediaPreview(
                            out.toByteArray(),
                            this::showMessageCamera
                        );
                    }catch(Exception ex){
                        toast(ex.getMessage());
                    }
                });
            }catch(Exception ex){
                toast(ex.getMessage());
            }
        });

        ImageView gallery=new ImageView(this);
        gallery.setScaleType(ImageView.ScaleType.CENTER_CROP);
        gallery.setBackground(bg(Color.rgb(38,38,38),11));
        gallery.setClipToOutline(true);
        FrameLayout.LayoutParams gp=
            new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.BOTTOM|Gravity.START);
        gp.leftMargin=dp(22);
        gp.bottomMargin=dp(18);
        page.addView(gallery,gp);
        loadLatestGalleryThumbnail(gallery);
        gallery.setOnClickListener(v->{
            d.dismiss();
            pickInstagramMediaFullScreen(2);
        });

        FrameLayout dotWrap=new FrameLayout(this);
        GradientDrawable dotOuter=new GradientDrawable();
        dotOuter.setShape(GradientDrawable.OVAL);
        dotOuter.setColor(Color.TRANSPARENT);
        dotOuter.setStroke(dp(2),Color.WHITE);
        dotWrap.setBackground(dotOuter);

        FrameLayout.LayoutParams dotWrapLp=
            new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.BOTTOM|Gravity.END);
        dotWrapLp.rightMargin=dp(22);
        dotWrapLp.bottomMargin=dp(20);
        page.addView(dotWrap,dotWrapLp);

        View dot=new View(this);
        GradientDrawable firstDot=
            new GradientDrawable(GradientDrawable.Orientation.TL_BR,palettes[0]);
        firstDot.setShape(GradientDrawable.OVAL);
        dot.setBackground(firstDot);
        dotWrap.addView(dot,new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER));

        dotWrap.setOnClickListener(v->{
            ix[0]=(ix[0]+1)%palettes.length;
            paint.run();
            GradientDrawable nextDot=
                new GradientDrawable(GradientDrawable.Orientation.TL_BR,palettes[ix[0]]);
            nextDot.setShape(GradientDrawable.OVAL);
            dot.setBackground(nextDot);
        });

        d.setContentView(page);
        d.show();

        Window w=d.getWindow();
        if(w!=null){
            w.setLayout(-1,-1);
            w.setStatusBarColor(Color.BLACK);
            w.setNavigationBarColor(Color.BLACK);
        }

        main.postDelayed(()->{
            input.requestFocus();
            InputMethodManager imm=
                (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
        },150);
    }

    private void pickInstagramMediaFullScreen(){
        pickInstagramMediaFullScreen(1);
    }

    private void pickInstagramMediaFullScreen(int previewOrigin){
        forceFullScreenMediaPicker=true;
        mediaPickerPreviewOrigin=previewOrigin;
        pickInstagramMedia();
    }

    private void loadLatestGalleryThumbnail(final ImageView target){
        if(target==null)return;
        target.setImageResource(R.drawable.ic_camera_gallery_exact_ref);
        new Thread(()->{
            Uri latest=null;
            String[] projection={android.provider.MediaStore.Images.Media._ID};
            String order=android.provider.MediaStore.Images.Media.DATE_ADDED+" DESC";
            try(android.database.Cursor c=getContentResolver().query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,null,null,order
            )){
                if(c!=null&&c.moveToFirst()){
                    long id=c.getLong(c.getColumnIndexOrThrow(
                        android.provider.MediaStore.Images.Media._ID
                    ));
                    latest=android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id
                    );
                }
            }catch(Exception ignored){}
            if(latest==null)return;
            final Uri uri=latest;
            try{
                final Bitmap thumb;
                if(Build.VERSION.SDK_INT>=29){
                    thumb=getContentResolver().loadThumbnail(
                        uri,new android.util.Size(dp(96),dp(96)),null
                    );
                }else{
                    thumb=android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                        getContentResolver(),android.content.ContentUris.parseId(uri),
                        android.provider.MediaStore.Images.Thumbnails.MINI_KIND,null
                    );
                }
                if(thumb!=null)main.post(()->target.setImageBitmap(thumb));
            }catch(Exception ignored){}
        }).start();
    }

    private boolean hasInstagramMediaPermission(){
        if(Build.VERSION.SDK_INT>=33){
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED ||
                   checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }

    private void pickInstagramMedia(){
        if(!hasInstagramMediaPermission()){
            if(Build.VERSION.SDK_INT>=33){
                requestPermissions(
                    new String[]{
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    },
                    REQ_MEDIA
                );
            }else{
                requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_MEDIA
                );
            }
            return;
        }
        showInstagramMediaPicker();
    }

    private void applyConversationPickerInset(int inset){
        if(list==null)return;

        int safe=Math.max(0,inset);

        // Undo the v57 layout-margin approach.
        ViewGroup.LayoutParams raw=list.getLayoutParams();
        if(raw instanceof LinearLayout.LayoutParams){
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)raw;
            if(lp.bottomMargin!=0){
                lp.bottomMargin=0;
                list.setLayoutParams(lp);
            }
        }

        // Keep normal conversation padding only.
        list.setPadding(dp(10),dp(12),dp(10),dp(5));
        list.setClipToPadding(true);

        // Move the entire conversation by exactly the same distance
        // as the composer. This preserves their normal spacing.
        list.setTranslationY(-safe);
    }

    private void showInstagramMediaPicker(){
        if(root==null||composer==null||activeConversation==null)return;

        final boolean fullScreenPicker=forceFullScreenMediaPicker;
        final int previewOrigin=mediaPickerPreviewOrigin;
        forceFullScreenMediaPicker=false;
        mediaPickerPreviewOrigin=0;

        View old=root.findViewWithTag("messenger-native-media-sheet");
        if(old!=null){
            dismissInstagramMediaPicker(old);
            return;
        }

        View stickerOld=root.findViewWithTag("messenger-native-sticker-sheet");
        if(stickerOld!=null){
            View stickerBridge=root.findViewWithTag("messenger-sticker-bridge");
            if(stickerOld.getParent()==root)root.removeView(stickerOld);
            if(stickerBridge!=null&&stickerBridge.getParent()==root)root.removeView(stickerBridge);
            if(composer!=null)composer.setTranslationY(0);
            if(replyBar!=null)replyBar.setTranslationY(0);
            if(list!=null){
                list.setTranslationY(0);
                list.setPadding(dp(10),dp(12),dp(10),dp(5));
            }
        }

        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null&&messageInput!=null){
            imm.hideSoftInputFromWindow(messageInput.getWindowToken(),0);
        }

        final int sheetH=fullScreenPicker
            ?getResources().getDisplayMetrics().heightPixels
            :Math.min(
                dp(500),
                (int)(getResources().getDisplayMetrics().heightPixels*.58f)
            );

        FrameLayout host=new FrameLayout(this);
        host.setTag("messenger-native-media-sheet");
        host.setClickable(true);
        host.setBackgroundColor(fullScreenPicker?Color.rgb(38,38,38):Color.TRANSPARENT);
        root.addView(
            host,
            new FrameLayout.LayoutParams(-1,sheetH,Gravity.BOTTOM)
        );

        View bridge=new View(this);
        bridge.setTag("messenger-media-bridge");
        bridge.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams bridgeLp=
            new FrameLayout.LayoutParams(-1,dp(4),Gravity.BOTTOM);
        bridgeLp.bottomMargin=sheetH-dp(1);
        root.addView(bridge,bridgeLp);

        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(topBg(Color.rgb(38,38,38),14));
        host.addView(sheet,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout dragHeader=new LinearLayout(this);
        dragHeader.setOrientation(LinearLayout.VERTICAL);
        dragHeader.setGravity(Gravity.CENTER_HORIZONTAL);
        dragHeader.setPadding(0,dp(5),0,dp(3));
        sheet.addView(dragHeader,new LinearLayout.LayoutParams(-1,dp(64)));

        View puller=new View(this);
        puller.setBackground(bg(Color.rgb(155,158,166),3));
        LinearLayout.LayoutParams pullLp=
            new LinearLayout.LayoutParams(dp(42),dp(5));
        pullLp.gravity=Gravity.CENTER_HORIZONTAL;
        pullLp.bottomMargin=dp(5);
        dragHeader.addView(puller,pullLp);

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER);
        titleRow.setPadding(0,0,0,0);
        dragHeader.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(44)));

        LinearLayout categoryButton=new LinearLayout(this);
        categoryButton.setGravity(Gravity.CENTER);
        categoryButton.setClickable(true);

        TextView title=text("Recents",18,Color.WHITE,Typeface.BOLD);
        categoryButton.addView(title,new LinearLayout.LayoutParams(-2,dp(44)));

        ImageView categoryArrow=new ImageView(this);
        categoryArrow.setImageResource(R.drawable.ic_media_chevron_down);
        categoryArrow.setColorFilter(Color.rgb(210,210,214));
        categoryArrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams arrowLp=new LinearLayout.LayoutParams(dp(15),dp(15));
        arrowLp.leftMargin=dp(5);
        categoryArrow.setTranslationY(dp(1));
        categoryButton.addView(categoryArrow,arrowLp);

        if(fullScreenPicker){
            ImageButton pickerBack=icon(R.drawable.ic_camera_back_ref,42,Color.WHITE);
            pickerBack.setBackgroundColor(Color.TRANSPARENT);
            titleRow.addView(pickerBack,new LinearLayout.LayoutParams(dp(44),dp(44)));
            titleRow.addView(categoryButton,new LinearLayout.LayoutParams(0,dp(44),1));
            Space balance=new Space(this);
            titleRow.addView(balance,new LinearLayout.LayoutParams(dp(44),dp(44)));
            pickerBack.setOnClickListener(v->{
                closeInstagramMediaPickerImmediately(host);
                if(previewOrigin==2)showMessageTextCreate();
                else showMessageCamera();
            });
        }else{
            titleRow.addView(categoryButton,new LinearLayout.LayoutParams(-2,dp(44)));
        }

        FrameLayout mediaHost=new FrameLayout(this);
        sheet.addView(mediaHost,new LinearLayout.LayoutParams(-1,0,1));

        TextView mediaDateBubble=text("",11.5f,Color.BLACK,Typeface.BOLD);
        mediaDateBubble.setGravity(Gravity.CENTER);
        mediaDateBubble.setPadding(dp(10),0,dp(10),0);
        mediaDateBubble.setBackground(bg(Color.WHITE,17));
        mediaDateBubble.setVisibility(View.GONE);
        mediaDateBubble.setElevation(dp(8));

        FrameLayout.LayoutParams mediaDateLp=
            new FrameLayout.LayoutParams(-2,dp(34),Gravity.TOP|Gravity.END);
        mediaDateLp.rightMargin=dp(22);
        mediaDateLp.topMargin=dp(8);
        mediaHost.addView(mediaDateBubble,mediaDateLp);

        View mediaScrollThumb=new View(this);
        mediaScrollThumb.setBackground(bg(Color.WHITE,4));
        FrameLayout.LayoutParams thumbLp=
            new FrameLayout.LayoutParams(dp(8),dp(42),Gravity.TOP|Gravity.END);
        thumbLp.rightMargin=dp(6);
        thumbLp.topMargin=dp(8);
        mediaHost.addView(mediaScrollThumb,thumbLp);

        final Runnable[] hideMediaDate={null};

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);

        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        mediaHost.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        categoryButton.setOnClickListener(v->showMediaCategoryMenu(title,categoryButton,grid));

        final List<Uri> selectedUris=new ArrayList<>();
        final List<Boolean> selectedVideos=new ArrayList<>();
        final int[] selectedViewMode={0};

        LinearLayout selectedBar=new LinearLayout(this);
        selectedBar.setGravity(Gravity.CENTER_VERTICAL);
        selectedBar.setPadding(dp(8),dp(5),dp(8),dp(5));
        selectedBar.setBackgroundColor(Color.argb(245,38,38,38));
        selectedBar.setVisibility(View.GONE);

        HorizontalScrollView selectedScroll=new HorizontalScrollView(this);
        selectedScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout selectedRow=new LinearLayout(this);
        selectedRow.setGravity(Gravity.CENTER_VERTICAL);

        selectedScroll.addView(
            selectedRow,
            new HorizontalScrollView.LayoutParams(-2,dp(58))
        );
        selectedBar.addView(
            selectedScroll,
            new LinearLayout.LayoutParams(0,dp(58),1)
        );

        TextView selectedMode=text("Unlimited views",12.5f,Color.WHITE,Typeface.BOLD);
        selectedMode.setGravity(Gravity.CENTER);
        selectedMode.setPadding(dp(8),0,dp(8),0);
        selectedMode.setBackground(bg(Color.rgb(58,58,62),17));
        selectedMode.setTag("media-picker-mode-button");
        setCapturedViewModeButton(selectedMode,0);
        LinearLayout.LayoutParams selectedModeLp=new LinearLayout.LayoutParams(dp(124),dp(34));
        selectedModeLp.leftMargin=dp(4);
        selectedBar.addView(selectedMode,selectedModeLp);
        selectedMode.setOnClickListener(v->showCapturedViewModeMenu(selectedMode,selectedViewMode));

        ImageButton sendSelected=icon(R.drawable.msg_send_enabled,28,Color.WHITE);
        sendSelected.setImageResource(R.drawable.msg_send_enabled);
        sendSelected.setColorFilter(Color.WHITE);
        sendSelected.setBackground(bg(Color.rgb(98,55,255),18));
        sendSelected.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendSelected.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout.LayoutParams sendLp=
            new LinearLayout.LayoutParams(dp(56),dp(32));
        sendLp.leftMargin=dp(7);
        selectedBar.addView(sendSelected,sendLp);

        FrameLayout.LayoutParams selectedLp=
            new FrameLayout.LayoutParams(-1,dp(68),Gravity.BOTTOM);
        mediaHost.addView(selectedBar,selectedLp);

        final Runnable[] renderSelectedHolder=new Runnable[1];

        renderSelectedHolder[0]=()->{
            selectedRow.removeAllViews();

            for(int si=0;si<selectedUris.size();si++){
                final int index=si;
                final Uri selectedUri=selectedUris.get(si);
                final boolean selectedVideo=selectedVideos.get(si);

                FrameLayout chip=new FrameLayout(MainActivity.this);
                LinearLayout.LayoutParams chipLp=
                    new LinearLayout.LayoutParams(dp(32),dp(52));
                chipLp.rightMargin=dp(6);
                selectedRow.addView(chip,chipLp);

                chip.setBackground(bg(Color.rgb(18,18,18),9));
                chip.setClipToOutline(true);

                ImageView image=new ImageView(MainActivity.this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(bg(Color.rgb(18,18,18),9));
                image.setClipToOutline(true);
                chip.addView(image,new FrameLayout.LayoutParams(-1,-1));

                new Thread(()->{
                    try{
                        Bitmap bm=getContentResolver().loadThumbnail(
                            selectedUri,
                            new android.util.Size(dp(90),dp(90)),
                            null
                        );
                        if(bm!=null)main.post(()->image.setImageBitmap(bm));
                    }catch(Exception ignored){}
                }).start();

                chip.setOnClickListener(v->
                    previewSelectedMedia(selectedUri,selectedVideo)
                );

                chip.setOnLongClickListener(v->{
                    if(index<selectedUris.size()){
                        selectedUris.remove(index);
                        selectedVideos.remove(index);
                        renderSelectedHolder[0].run();
                    }
                    return true;
                });
            }

            boolean has=!selectedUris.isEmpty();
            selectedBar.setVisibility(has?View.VISIBLE:View.GONE);
            scroll.setPadding(0,0,0,has?dp(70):0);
            scroll.setClipToPadding(false);
        };

        sendSelected.setOnClickListener(v->{
            if(selectedUris.isEmpty())return;

            final List<Uri> sendUris=new ArrayList<>(selectedUris);
            final List<Boolean> sendVideos=new ArrayList<>(selectedVideos);
            final int sendViewMode=selectedViewMode[0];

            selectedUris.clear();
            selectedVideos.clear();
            renderSelectedHolder[0].run();

            new Thread(()->{
                List<byte[]> batchBytes=new ArrayList<>();
                List<String> batchNames=new ArrayList<>(),batchMimes=new ArrayList<>();
                for(int si=0;si<sendUris.size();si++)try{
                    Uri sendUri=sendUris.get(si);boolean sendVideo=sendVideos.get(si);
                    String name=queryName(sendUri),mime=getContentResolver().getType(sendUri);
                    byte[] bytes=readAll(getContentResolver().openInputStream(sendUri));
                    if(bytes.length>27*1024*1024){main.post(()->toast("File is too large."));continue;}
                    batchBytes.add(bytes);batchNames.add(name);batchMimes.add(mime==null?(sendVideo?"video/mp4":"image/jpeg"):mime);
                }catch(Exception ex){main.post(()->toast(ex.getMessage()));}
                main.post(()->{
                    for(int i=0;i<batchBytes.size();i++)uploadAttachment(batchBytes.get(i),batchNames.get(i),batchMimes.get(i),sendViewMode,true);
                    if(!batchBytes.isEmpty()){if(messageAdapter!=null){messageAdapter.notifyDataSetChanged();scrollToAbsoluteBottom();}cacheMessagesNow();}
                });
            }).start();

            dismissInstagramMediaPicker(host);
        });

        ProgressBar loading=new ProgressBar(this);
        mediaHost.addView(
            loading,
            new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER)
        );

        final float[] startY={Float.NaN};

        View.OnTouchListener drag=(v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    startY[0]=e.getRawY()-host.getTranslationY();
                    host.animate().cancel();
                    bridge.animate().cancel();
                    composer.animate().cancel();
                    if(replyBar!=null)replyBar.animate().cancel();
                    ViewParent p0=v.getParent();
                    if(p0!=null)p0.requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if(Float.isNaN(startY[0]))return true;
                    float dy=Math.max(0,e.getRawY()-startY[0]);
                    host.setTranslationY(dy);
                    bridge.setTranslationY(dy);
                    composer.setTranslationY(-sheetH+dy);
                    applyConversationPickerInset(Math.max(0,sheetH-(int)dy));
                    if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE){
                        replyBar.setTranslationY(-sheetH+dy);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float y=host.getTranslationY();
                    startY[0]=Float.NaN;
                    ViewParent p1=v.getParent();
                    if(p1!=null)p1.requestDisallowInterceptTouchEvent(false);

                    if(y>dp(76)){
                        dismissInstagramMediaPicker(host);
                    }else{
                        host.animate().translationY(0).setDuration(190)
                            .setInterpolator(new DecelerateInterpolator()).start();
                        bridge.animate().translationY(0).setDuration(190)
                            .setInterpolator(new DecelerateInterpolator()).start();
                        applyConversationPickerInset(sheetH);
                        composer.animate().translationY(-sheetH).setDuration(190)
                            .setInterpolator(new DecelerateInterpolator()).start();
                        if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE){
                            replyBar.animate().translationY(-sheetH).setDuration(190)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        }
                    }
                    return true;
            }
            return true;
        };
        if(!fullScreenPicker)dragHeader.setOnTouchListener(drag);

        final float[] contentDownY={Float.NaN};
        final boolean[] contentSheetDrag={false};

        View.OnTouchListener contentDrag=(v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    contentDownY[0]=e.getRawY();
                    contentSheetDrag[0]=false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    float delta=e.getRawY()-contentDownY[0];

                    if(
                        !contentSheetDrag[0] &&
                        scroll.getScrollY()<=dp(3) &&
                        delta>dp(1)
                    ){
                        contentSheetDrag[0]=true;
                        startY[0]=contentDownY[0]-host.getTranslationY();
                        host.animate().cancel();
                        bridge.animate().cancel();
                        composer.animate().cancel();
                        if(replyBar!=null)replyBar.animate().cancel();
                        ViewParent parent=v.getParent();
                        if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);
                    }

                    if(contentSheetDrag[0]){
                        float dy=Math.max(0,e.getRawY()-startY[0]);
                        host.setTranslationY(dy);
                        bridge.setTranslationY(dy);
                        composer.setTranslationY(-sheetH+dy);
                    applyConversationPickerInset(Math.max(0,sheetH-(int)dy));
                        if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE){
                            replyBar.setTranslationY(-sheetH+dy);
                        }
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(contentSheetDrag[0]){
                        float y=host.getTranslationY();
                        contentSheetDrag[0]=false;
                        contentDownY[0]=Float.NaN;

                        ViewParent parent=v.getParent();
                        if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);

                        if(y>dp(48)){
                            dismissInstagramMediaPicker(host);
                        }else{
                            host.animate().translationY(0).setDuration(190)
                                .setInterpolator(new DecelerateInterpolator()).start();
                            bridge.animate().translationY(0).setDuration(190)
                                .setInterpolator(new DecelerateInterpolator()).start();
                            applyConversationPickerInset(sheetH);
                            composer.animate().translationY(-sheetH).setDuration(190)
                                .setInterpolator(new DecelerateInterpolator()).start();
                            if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE){
                                replyBar.animate().translationY(-sheetH).setDuration(190)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                            }
                        }
                        return true;
                    }

                    contentDownY[0]=Float.NaN;
                    return false;
            }
            return false;
        };

        if(!fullScreenPicker){
            scroll.setOnTouchListener(contentDrag);
            grid.setOnTouchListener(contentDrag);
            mediaHost.setOnTouchListener(contentDrag);
            sheet.setOnTouchListener((v,e)->{
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN && e.getY()<=dp(72)){
                    return false;
                }
                return contentDrag.onTouch(v,e);
            });

            host.setTranslationY(sheetH);
            bridge.setTranslationY(sheetH);
            composer.setTranslationY(0);
            if(replyBar!=null)replyBar.setTranslationY(0);
            applyConversationPickerInset(sheetH);
            host.animate().translationY(0).setDuration(330)
                .setInterpolator(new DecelerateInterpolator()).start();
            bridge.animate().translationY(0).setDuration(330)
                .setInterpolator(new DecelerateInterpolator()).start();
            composer.animate().translationY(-sheetH).setDuration(330)
                .setInterpolator(new DecelerateInterpolator()).start();
            if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE){
                replyBar.animate().translationY(-sheetH).setDuration(330)
                    .setInterpolator(new DecelerateInterpolator()).start();
            }
        }else{
            host.setTranslationY(0);
            bridge.setTranslationY(0);
            applyConversationPickerInset(0);
            if(composer!=null)composer.setTranslationY(0);
            if(replyBar!=null)replyBar.setTranslationY(0);
        }

        new Thread(()->{
            final List<Uri> uris=new ArrayList<>();
            final List<Boolean> videos=new ArrayList<>();
            final List<Boolean> favorites=new ArrayList<>();
            final List<Long> mediaDates=new ArrayList<>();

            Uri collection=android.provider.MediaStore.Files.getContentUri("external");
            String favoriteProjection=
                Build.VERSION.SDK_INT>=30
                    ?android.provider.MediaStore.MediaColumns.IS_FAVORITE
                    :android.provider.MediaStore.Files.FileColumns._ID;

            String[] projection={
                android.provider.MediaStore.Files.FileColumns._ID,
                android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE,
                favoriteProjection,
                android.provider.MediaStore.Files.FileColumns.DATE_ADDED
            };
            String selection=
                android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE+"=? OR "+
                android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE+"=?";
            String[] args={
                String.valueOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                String.valueOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            };
            String order=android.provider.MediaStore.Files.FileColumns.DATE_ADDED+" DESC";

            try(android.database.Cursor c=getContentResolver().query(
                collection,projection,selection,args,order
            )){
                if(c!=null){
                    int idCol=c.getColumnIndexOrThrow(
                        android.provider.MediaStore.Files.FileColumns._ID
                    );
                    int typeCol=c.getColumnIndexOrThrow(
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE
                    );
                    int favoriteCol=
                        Build.VERSION.SDK_INT>=30
                            ?c.getColumnIndex(android.provider.MediaStore.MediaColumns.IS_FAVORITE)
                            :-1;
                    int dateCol=c.getColumnIndex(
                        android.provider.MediaStore.Files.FileColumns.DATE_ADDED
                    );
                    int count=0;

                    while(c.moveToNext()&&count<240){
                        long id=c.getLong(idCol);
                        int mt=c.getInt(typeCol);
                        Uri item=android.content.ContentUris.withAppendedId(collection,id);
                        uris.add(item);
                        videos.add(
                            mt==android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        );
                        favorites.add(
                            favoriteCol>=0 && c.getInt(favoriteCol)==1
                        );
                        mediaDates.add(dateCol>=0?c.getLong(dateCol):0L);
                        count++;
                    }
                }
            }catch(Exception ignored){}

            main.post(()->{
                if(host.getParent()==null)return;

                loading.setVisibility(View.GONE);
                grid.removeAllViews();
                int cell=getResources().getDisplayMetrics().widthPixels/3;

                if(Build.VERSION.SDK_INT>=23){
                    scroll.setOnScrollChangeListener(
                        (View v,int sx,int sy,int ox,int oy)->{
                            if(mediaDates.isEmpty())return;

                            int idx=Math.min(
                                mediaDates.size()-1,
                                Math.max(0,(sy/Math.max(1,cell))*3)
                            );
                            long sec=mediaDates.get(idx);
                            if(sec<=0)return;

                            mediaDateBubble.setText(
                                new SimpleDateFormat(
                                    "MMM d yyyy",
                                    Locale.US
                                ).format(new Date(sec*1000L))
                            );
                            mediaDateBubble.setVisibility(View.VISIBLE);

                            int contentH=Math.max(
                                scroll.getHeight(),
                                grid.getHeight()
                            );
                            int viewportH=Math.max(1,scroll.getHeight());
                            int maxScroll=Math.max(1,contentH-viewportH);

                            float progress=Math.max(
                                0f,
                                Math.min(1f,sy/(float)maxScroll)
                            );

                            int track=Math.max(
                                0,
                                mediaHost.getHeight()-dp(58)
                            );
                            float thumbY=dp(8)+(track*progress);

                            mediaScrollThumb.setTranslationY(thumbY);
                            mediaDateBubble.setTranslationY(
                                thumbY+dp(9)
                            );

                            mediaScrollThumb.bringToFront();
                            mediaDateBubble.bringToFront();

                            if(hideMediaDate[0]!=null){
                                main.removeCallbacks(hideMediaDate[0]);
                            }
                            hideMediaDate[0]=()->
                                mediaDateBubble.setVisibility(View.GONE);
                            main.postDelayed(hideMediaDate[0],520);
                        }
                    );
                }

                for(int i=0;i<uris.size();i++){
                    final Uri uri=uris.get(i);
                    final boolean video=videos.get(i);
                    final boolean favorite=favorites.size()>i&&favorites.get(i);

                    FrameLayout tile=new FrameLayout(MainActivity.this);
                    tile.setTag((video?"video":"photo")+"|"+(favorite?"favorite":"normal"));
                    GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
                    gp.width=cell;
                    gp.height=cell;
                    gp.setMargins(1,1,1,1);
                    grid.addView(tile,gp);

                    ImageView thumb=new ImageView(MainActivity.this);
                    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    thumb.setBackgroundColor(Color.rgb(18,18,18));
                    tile.addView(thumb,new FrameLayout.LayoutParams(-1,-1));

                    if(video){
                        TextView play=text("▶",14,Color.WHITE,Typeface.BOLD);
                        play.setGravity(Gravity.CENTER);
                        FrameLayout.LayoutParams pp=
                            new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.BOTTOM|Gravity.START);
                        pp.leftMargin=dp(7);
                        pp.bottomMargin=dp(6);
                        tile.addView(play,pp);
                    }

                    new Thread(()->{
                        try{
                            Bitmap bm;
                            if(Build.VERSION.SDK_INT>=29){
                                bm=getContentResolver().loadThumbnail(
                                    uri,
                                    new android.util.Size(cell,cell),
                                    null
                                );
                            }else{
                                bm=android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                                    getContentResolver(),
                                    android.content.ContentUris.parseId(uri),
                                    android.provider.MediaStore.Images.Thumbnails.MINI_KIND,
                                    null
                                );
                            }

                            if(bm!=null)main.post(()->{
                                if(host.getParent()!=null)thumb.setImageBitmap(bm);
                            });
                        }catch(Exception ignored){}
                    }).start();
                    TextView selectBadge=text(
                        "",
                        12,
                        Color.WHITE,
                        Typeface.BOLD
                    );
                    selectBadge.setGravity(Gravity.CENTER);
                    selectBadge.setBackground(bg(Color.argb(135,55,55,55),13));

                    FrameLayout.LayoutParams badgeLp=
                        new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.TOP|Gravity.END);
                    badgeLp.topMargin=dp(7);
                    badgeLp.rightMargin=dp(7);
                    tile.addView(selectBadge,badgeLp);

                    Runnable refreshBadge=()->{
                        int selectedIndex=selectedUris.indexOf(uri);
                        if(selectedIndex>=0){
                            selectBadge.setText(String.valueOf(selectedIndex+1));
                            selectBadge.setBackground(bg(Color.rgb(91,70,246),13));
                        }else{
                            selectBadge.setText("");
                            selectBadge.setBackground(bg(Color.argb(135,55,55,55),13));
                        }
                    };
                    refreshBadge.run();

                    final float[] tileDownY={Float.NaN};
                    final boolean[] tilePulling={false};

                    if(!fullScreenPicker)tile.setOnTouchListener((v,e)->{
                        switch(e.getActionMasked()){
                            case MotionEvent.ACTION_DOWN:
                                tileDownY[0]=e.getRawY();
                                tilePulling[0]=false;
                                return false;

                            case MotionEvent.ACTION_MOVE:
                                if(Float.isNaN(tileDownY[0]))return false;
                                float pull=e.getRawY()-tileDownY[0];

                                if(
                                    !tilePulling[0] &&
                                    scroll.getScrollY()<=dp(3) &&
                                    pull>dp(3)
                                ){
                                    tilePulling[0]=true;
                                    startY[0]=tileDownY[0]-host.getTranslationY();

                                    host.animate().cancel();
                                    bridge.animate().cancel();
                                    composer.animate().cancel();
                                    if(replyBar!=null)replyBar.animate().cancel();

                                    ViewParent parent=v.getParent();
                                    if(parent!=null){
                                        parent.requestDisallowInterceptTouchEvent(true);
                                    }
                                }

                                if(tilePulling[0]){
                                    float dy=Math.max(
                                        0,
                                        e.getRawY()-startY[0]
                                    );

                                    host.setTranslationY(dy);
                                    bridge.setTranslationY(dy);
                                    composer.setTranslationY(-sheetH+dy);
                                    applyConversationPickerInset(
                                        Math.max(0,sheetH-(int)dy)
                                    );

                                    if(
                                        replyBar!=null &&
                                        replyBar.getVisibility()==View.VISIBLE
                                    ){
                                        replyBar.setTranslationY(-sheetH+dy);
                                    }
                                    return true;
                                }
                                return false;

                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                if(tilePulling[0]){
                                    float y=host.getTranslationY();
                                    tilePulling[0]=false;
                                    tileDownY[0]=Float.NaN;

                                    ViewParent parent=v.getParent();
                                    if(parent!=null){
                                        parent.requestDisallowInterceptTouchEvent(false);
                                    }

                                    if(y>dp(40)){
                                        dismissInstagramMediaPicker(host);
                                    }else{
                                        host.animate()
                                            .translationY(0)
                                            .setDuration(170)
                                            .setInterpolator(new DecelerateInterpolator())
                                            .start();

                                        bridge.animate()
                                            .translationY(0)
                                            .setDuration(170)
                                            .setInterpolator(new DecelerateInterpolator())
                                            .start();

                                        applyConversationPickerInset(sheetH);

                                        composer.animate()
                                            .translationY(-sheetH)
                                            .setDuration(170)
                                            .setInterpolator(new DecelerateInterpolator())
                                            .start();

                                        if(
                                            replyBar!=null &&
                                            replyBar.getVisibility()==View.VISIBLE
                                        ){
                                            replyBar.animate()
                                                .translationY(-sheetH)
                                                .setDuration(170)
                                                .setInterpolator(new DecelerateInterpolator())
                                                .start();
                                        }
                                    }
                                    return true;
                                }

                                tileDownY[0]=Float.NaN;
                                return false;
                        }
                        return false;
                    });

                    tile.setOnClickListener(v->{
                        if(fullScreenPicker&&previewOrigin>0){
                            if(video){
                                previewSelectedMedia(uri,true);
                            }else{
                                openPickedPhotoAsCaptured(uri,previewOrigin,host);
                            }
                            return;
                        }
                        int existing=selectedUris.indexOf(uri);

                        if(existing>=0){
                            selectedUris.remove(existing);
                            selectedVideos.remove(existing);
                        }else{
                            selectedUris.add(uri);
                            selectedVideos.add(video);
                        }

                        renderSelectedHolder[0].run();
                        refreshBadge.run();
                    });
                }
            });
        }).start();
    }

    private void openPickedPhotoAsCaptured(final Uri uri,final int previewOrigin,final View host){
        if(uri==null)return;
        new Thread(()->{
            try{
                final byte[] bytes=readAll(getContentResolver().openInputStream(uri));
                if(bytes.length==0)throw new Exception("Could not read this photo.");
                main.post(()->{
                    closeInstagramMediaPickerImmediately(host);
                    Runnable back=previewOrigin==2
                        ?()->showMessageTextCreate()
                        :()->showMessageCamera();
                    showCapturedMediaPreview(bytes,back);
                });
            }catch(Exception e){
                main.post(()->toast(e.getMessage()));
            }
        }).start();
    }

    private void closeInstagramMediaPickerImmediately(View host){
        if(root==null)return;
        View bridge=root.findViewWithTag("messenger-media-bridge");
        if(host!=null&&host.getParent()==root)root.removeView(host);
        if(bridge!=null&&bridge.getParent()==root)root.removeView(bridge);
        applyConversationPickerInset(0);
        if(composer!=null)composer.setTranslationY(0);
        if(replyBar!=null)replyBar.setTranslationY(0);
    }

    private void previewSelectedMedia(Uri uri,boolean video){
        if(uri==null)return;

        final Dialog d=new Dialog(
            this,
            android.R.style.Theme_Black_NoTitleBar_Fullscreen
        );

        FrameLayout previewRoot=new FrameLayout(this);
        previewRoot.setBackgroundColor(Color.BLACK);

        if(video){
            VideoView vv=new VideoView(this);
            vv.setVideoURI(uri);
            vv.setOnPreparedListener(mp->{
                mp.setLooping(true);
                vv.start();
            });
            previewRoot.addView(vv,new FrameLayout.LayoutParams(-1,-1));
        }else{
            ImageView iv=new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            previewRoot.addView(iv,new FrameLayout.LayoutParams(-1,-1));

            new Thread(()->{
                try{
                    Bitmap bm=BitmapFactory.decodeStream(
                        getContentResolver().openInputStream(uri)
                    );
                    if(bm!=null)main.post(()->iv.setImageBitmap(bm));
                }catch(Exception ignored){}
            }).start();
        }

        ImageButton close=icon(R.drawable.ic_msg_close,42,Color.WHITE);
        FrameLayout.LayoutParams closeLp=
            new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.TOP|Gravity.START);
        closeLp.leftMargin=dp(10);
        closeLp.topMargin=dp(10);
        previewRoot.addView(close,closeLp);
        close.setOnClickListener(v->d.dismiss());

        d.setContentView(previewRoot);
        d.show();

        Window w=d.getWindow();
        if(w!=null){
            w.setStatusBarColor(Color.BLACK);
            w.setNavigationBarColor(Color.BLACK);
            w.setLayout(-1,-1);
        }
    }

    private void showMediaCategoryMenu(
        TextView title,
        View anchor,
        GridLayout grid
    ){
        final android.widget.PopupWindow popup=
            new android.widget.PopupWindow(this);

        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8),dp(7),dp(8),dp(7));
        card.setBackground(bg(Color.argb(250,36,39,43),20));

        String[] labels={
            "Recents",
            "Favorites",
            "Photos",
            "Videos",
            "All albums"
        };
        String[] modes={
            "all",
            "favorite",
            "photo",
            "video",
            "all"
        };
        int[] icons={
            R.drawable.ic_media_recents,
            R.drawable.ic_media_favorite,
            R.drawable.ic_media_photos,
            R.drawable.ic_media_videos,
            R.drawable.ic_media_albums
        };

        for(int index=0;index<labels.length;index++){
            final String label=labels[index];
            final String mode=modes[index];

            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10),0,dp(10),0);

            ImageView icon=new ImageView(this);
            icon.setImageResource(icons[index]);
            icon.setColorFilter(Color.WHITE);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(50)));

            TextView rowText=text(label,18,Color.WHITE,Typeface.NORMAL);
            LinearLayout.LayoutParams textLp=
                new LinearLayout.LayoutParams(0,dp(50),1);
            textLp.leftMargin=dp(8);
            row.addView(rowText,textLp);

            card.addView(row,new LinearLayout.LayoutParams(dp(252),dp(50)));

            row.setOnClickListener(v->{
                popup.dismiss();
                title.setText(label);

                for(int i=0;i<grid.getChildCount();i++){
                    View child=grid.getChildAt(i);
                    Object rawTag=child.getTag();
                    if(rawTag==null)continue;

                    String tag=String.valueOf(rawTag);
                    boolean show;

                    if("favorite".equals(mode)){
                        show=tag.endsWith("|favorite");
                    }else if("photo".equals(mode)){
                        show=tag.startsWith("photo|");
                    }else if("video".equals(mode)){
                        show=tag.startsWith("video|");
                    }else{
                        show=true;
                    }

                    child.setVisibility(show?View.VISIBLE:View.GONE);
                }
            });
        }

        popup.setContentView(card);
        popup.setWidth(dp(268));
        popup.setHeight(-2);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        );
        popup.setElevation(dp(10));
        popup.showAsDropDown(anchor,-dp(8),-dp(2));
    }

    private void dismissInstagramMediaPicker(View host){
        if(host==null||root==null)return;
        applyConversationPickerInset(0);

        View bridge=root.findViewWithTag("messenger-media-bridge");
        int h=Math.max(host.getHeight(),dp(360));

        host.animate()
            .translationY(h)
            .setDuration(220)
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .withEndAction(()->{
                if(host.getParent()==root)root.removeView(host);
            })
            .start();

        if(bridge!=null){
            bridge.animate()
                .translationY(h)
                .setDuration(220)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(()->{
                    if(bridge.getParent()==root)root.removeView(bridge);
                })
                .start();
        }

        if(composer!=null){
            composer.animate().translationY(0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
        }

        if(replyBar!=null){
            replyBar.animate().translationY(0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void pickAttachment(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*","audio/*","application/pdf","text/plain","application/zip"});startActivityForResult(i,PICK_FILE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(requestCode==PICK_GROUP_IMAGE){try{byte[] bytes=readAll(getContentResolver().openInputStream(uri));Bitmap src=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(src==null)throw new Exception("Could not read this image.");int side=Math.min(src.getWidth(),src.getHeight()),sx=(src.getWidth()-side)/2,sy=(src.getHeight()-side)/2;Bitmap crop=Bitmap.createBitmap(src,sx,sy,side,side);Bitmap small=Bitmap.createScaledBitmap(crop,240,240,true);ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,72,out);groupEditImageData="data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);groupEditEmoji="";if(groupEditConversation!=null)showGroupEditor(groupEditConversation);}catch(Exception e){toast(e.getMessage());}return;}if(requestCode!=PICK_FILE)return;try{String name=queryName(uri),mime=getContentResolver().getType(uri);byte[] bytes=readAll(getContentResolver().openInputStream(uri));if(bytes.length>27*1024*1024){toast("File is too large.");return;}uploadAttachment(bytes,name,mime==null?"application/octet-stream":mime);}catch(Exception e){toast(e.getMessage());}}
    private String queryName(Uri u){String name="attachment";try(android.database.Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}return name==null?"attachment":name;}
    private byte[] readAll(InputStream in)throws Exception{try(in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}}
    private void uploadAttachment(byte[] bytes,String name,String mime){uploadAttachment(bytes,name,mime,0);}
    private String markedMediaName(String name,int viewMode){String clean=name==null||name.trim().isEmpty()?"attachment":name.trim();clean=clean.replaceFirst("^__vm[12]__","");return viewMode==1?"__vm1__"+clean:viewMode==2?"__vm2__"+clean:clean;}
    private JSONObject buildOptimisticMedia(byte[] bytes,String storedName,String mime,String client,JSONObject reply,int viewMode){JSONObject temp=new JSONObject();try{String type=mime!=null&&mime.toLowerCase(Locale.ROOT).startsWith("video/")?"video":mime!=null&&mime.toLowerCase(Locale.ROOT).startsWith("image/")?"image":"file";File local=new File(getCacheDir(),"pending-"+client+"-"+storedName.replaceAll("[^A-Za-z0-9._-]","_"));try(FileOutputStream out=new FileOutputStream(local)){out.write(bytes);}JSONObject attachment=new JSONObject().put("url",Uri.fromFile(local).toString()).put("localPreviewUrl",Uri.fromFile(local).toString()).put("name",storedName).put("mime",mime==null?"application/octet-stream":mime).put("size",bytes.length).put("viewMode",viewMode).put("mediaViewMode",viewMode);if("image".equals(type)){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth>0&&bounds.outHeight>0)attachment.put("width",bounds.outWidth).put("height",bounds.outHeight);}temp.put("id","tmp-"+System.nanoTime()).put("clientId",client).put("conversationId",activeConversation==null?"":activeConversation.optString("id")).put("senderId",selfId).put("type",type).put("body","").put("createdAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date())).put("status","sending").put("pending",true).put("optimisticRetainUntil",System.currentTimeMillis()+30000L).put("viewMode",viewMode).put("mediaViewMode",viewMode).put("sender",new JSONObject().put("id",selfId).put("name","You").put("isSelf",true)).put("attachments",new JSONArray().put(attachment)).put("reactions",new JSONArray());if(reply!=null)temp.put("reply",new JSONObject().put("id",reply.optString("id")).put("body",reply.optString("body")).put("type",reply.optString("type","text")).put("senderName",senderName(reply)));}catch(Exception e){return null;}return temp;}
    private void uploadAttachment(byte[] bytes,String name,String mime,int viewMode){uploadAttachment(bytes,name,mime,viewMode,false);}
    private void uploadAttachment(byte[] bytes,String name,String mime,int viewMode,boolean deferVisualRefresh){if(activeConversation==null||bytes==null||bytes.length==0)return;int actualMode=Math.max(0,Math.min(2,viewMode));String cid=activeConversation.optString("id"),client="native-"+UUID.randomUUID(),storedName=markedMediaName(name,actualMode);JSONObject replyObj=replyTo;String reply=replyObj==null?"":replyObj.optString("id");JSONObject optimistic=buildOptimisticMedia(bytes,storedName,mime,client,replyObj,actualMode);if(optimistic!=null){messages.add(optimistic);if(!deferVisualRefresh){if(messageAdapter!=null){messageAdapter.notifyDataSetChanged();scrollToAbsoluteBottom();}cacheMessagesNow();}}setReply(null);api.upload("/api/messaging/conversations/"+cid+"/attachment",bytes,storedName,mime,"",client,reply,actualMode,(json,error)->main.post(()->{if(error!=null){if(optimistic!=null)markOptimisticFailed(client);toast(error.getMessage());cacheMessagesNow();return;}JSONObject m=json.optJSONObject("message");if(m!=null){try{m.put("viewMode",actualMode).put("mediaViewMode",actualMode);JSONArray serverAttachments=m.optJSONArray("attachments");if(serverAttachments!=null&&serverAttachments.length()>0){JSONObject serverAttachment=serverAttachments.optJSONObject(0);if(serverAttachment!=null){serverAttachment.put("viewMode",actualMode).put("mediaViewMode",actualMode);if(serverAttachment.optString("name").isEmpty())serverAttachment.put("name",storedName);}}}catch(Exception ignored){}replaceOptimistic(client,m);cacheMessagesNow();refreshInbox();}}));}

    private boolean isFingerOver(View target,float rawX,float rawY,int extraDp){if(target==null||target.getVisibility()!=View.VISIBLE)return false;int[] loc=new int[2];target.getLocationOnScreen(loc);float ex=dp(extraDp);return rawX>=loc[0]-ex&&rawX<=loc[0]+target.getWidth()+ex&&rawY>=loc[1]-ex&&rawY<=loc[1]+target.getHeight()+ex;}
    private void syncRecordGestureUi(float rawX,float rawY){
        boolean overDelete=isFingerOver(recordCancelButton,rawX,rawY,2);recordCanceled=overDelete;
        if(overDelete!=recordDeleteHot){recordDeleteHot=overDelete;if(overDelete)vibrateSafe(38);}
        if(recordCancelButton!=null){ViewGroup.LayoutParams lp=recordCancelButton.getLayoutParams();int target=dp(overDelete?38:32);if(lp.width!=target||lp.height!=target){lp.width=target;lp.height=target;recordCancelButton.setLayoutParams(lp);}recordCancelButton.setRotation(0f);recordCancelButton.setPadding(dp(overDelete?8:7),dp(overDelete?8:7),dp(overDelete?8:7),dp(overDelete?8:7));recordCancelButton.setBackground(bg(overDelete?Color.rgb(235,42,58):Color.WHITE,(overDelete?19:16)));recordCancelButton.setColorFilter(overDelete?Color.WHITE:Color.rgb(190,193,198));}
        float dy=rawY-recordDownY;boolean lockReady=!overDelete&&dy<-dp(72);recordLockReady=lockReady;
        if(lockReady!=recordLockHot){recordLockHot=lockReady;if(lockReady)vibrateSafe(38);}
        if(recordLockIndicator!=null){recordLockIndicator.setVisibility((dy<-dp(34)||lockReady)?View.VISIBLE:View.GONE);recordLockIndicator.setScaleX(lockReady?1.24f:1f);recordLockIndicator.setScaleY(lockReady?1.24f:1f);}
        if(recordLockHint!=null){recordLockHint.setText(lockReady?"Release to lock":overDelete?"Release to cancel":"");recordLockHint.setVisibility((lockReady||overDelete)?View.VISIBLE:View.GONE);}
    }
    private boolean handleMicTouch(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){recordDownX=e.getRawX();recordDownY=e.getRawY();recordLocked=false;recordCanceled=false;recordLockReady=false;recordDeleteHot=false;recordLockHot=false;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingMicStart=true;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return true;}startRecorder();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&recorder!=null&&!recordLocked){syncRecordGestureUi(e.getRawX(),e.getRawY());return true;}
        if((e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)&&recorder!=null){
            boolean cancel=e.getActionMasked()==MotionEvent.ACTION_CANCEL||recordCanceled,lock=recordLockReady&&!cancel;
            if(cancel){stopRecorder(false);return true;}
            if(lock){recordLocked=true;recordLockReady=false;if(recordLockIndicator!=null)recordLockIndicator.setVisibility(View.GONE);if(recordLockHint!=null)recordLockHint.setVisibility(View.GONE);if(recordCancelButton!=null){recordCancelButton.setColorFilter(Color.rgb(160,164,170));recordCancelButton.setRotation(0);}showRecordingLocked();return true;}
            if(recordLocked)return true;stopRecorder(true);return true;
        }return true;
    }
    private void startRecorder(){if(recorder!=null)return;try{recordFile=new File(getCacheDir(),"voice-"+System.currentTimeMillis()+".m4a");recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(96000);recorder.setAudioSamplingRate(44100);recorder.setOutputFile(recordFile.getAbsolutePath());recorder.prepare();recorder.start();recordStarted=System.currentTimeMillis();showRecordBar();}catch(Exception e){try{if(recorder!=null)recorder.release();}catch(Exception ignored){}recorder=null;toast("Could not start voice recording.");}}
    private void showRecordBar(){
        if(composer==null||recordOverlay!=null)return;composer.setAlpha(0f);
        recordOverlay=new FrameLayout(this);recordOverlay.setClipChildren(false);recordOverlay.setClipToPadding(false);root.addView(recordOverlay,new FrameLayout.LayoutParams(-1,-1));
        recordBar=new LinearLayout(this);recordBar.setGravity(Gravity.CENTER_VERTICAL);recordBar.setPadding(dp(5),dp(4),dp(6),dp(4));GradientDrawable recordingBg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(91,73,247),Color.rgb(79,83,255),Color.rgb(82,80,248)});recordingBg.setCornerRadius(dp(28));recordBar.setBackground(recordingBg);
        FrameLayout.LayoutParams rlp=new FrameLayout.LayoutParams(-1,dp(56),Gravity.BOTTOM);rlp.setMargins(dp(7),0,dp(7),dp(3));recordOverlay.addView(recordBar,rlp);
        recordCancelButton=icon(R.drawable.voice_trash_user,32,Color.rgb(190,193,198));recordCancelButton.setBackground(bg(Color.WHITE,16));recordCancelButton.setPadding(dp(7),dp(7),dp(7),dp(7));recordBar.addView(recordCancelButton,new LinearLayout.LayoutParams(dp(32),dp(32)));recordCancelButton.setOnClickListener(v->stopRecorder(false));
        FrameLayout waveHolder=new FrameLayout(this);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,dp(46),1);wp.leftMargin=dp(9);wp.rightMargin=dp(7);recordBar.addView(waveHolder,wp);LinearLayout wave=new LinearLayout(this);wave.setGravity(Gravity.CENTER_VERTICAL);waveHolder.addView(wave,new FrameLayout.LayoutParams(-1,-1));for(int i=0;i<recordLevels.length;i++){View bar=new View(this);bar.setBackground(bg(Color.WHITE,2));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(2),dp(2));bp.setMargins(dp(1),0,dp(1),0);wave.addView(bar,bp);recordLevels[i]=0f;}
        TextView timer=text("0:00",15,Color.WHITE,Typeface.NORMAL);timer.setGravity(Gravity.CENTER);recordBar.addView(timer,new LinearLayout.LayoutParams(dp(58),dp(46)));
        ImageButton send=icon(R.drawable.ic_voice_send_up,46,Color.WHITE);send.setPadding(dp(7),dp(7),dp(7),dp(7));recordBar.addView(send,new LinearLayout.LayoutParams(dp(46),dp(46)));send.setOnClickListener(v->stopRecorder(true));
        recordLockHint=text("",13,Color.WHITE,Typeface.BOLD);recordLockHint.setGravity(Gravity.CENTER);recordLockHint.setBackground(bg(0x88000000,14));recordLockHint.setPadding(dp(12),0,dp(12),0);recordLockHint.setVisibility(View.GONE);FrameLayout.LayoutParams ghp=new FrameLayout.LayoutParams(-2,dp(30),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);ghp.bottomMargin=dp(64);recordOverlay.addView(recordLockHint,ghp);
        LockView lock=new LockView(this);recordLockIndicator=lock;recordLockIndicator.setVisibility(View.GONE);FrameLayout.LayoutParams lip=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.END|Gravity.BOTTOM);lip.rightMargin=dp(12);lip.bottomMargin=dp(150);recordOverlay.addView(recordLockIndicator,lip);
        recordTicker=new Runnable(){public void run(){if(recorder==null)return;long ms=System.currentTimeMillis()-recordStarted;timer.setText((ms/60000)+":"+String.format(Locale.US,"%02d",(ms/1000)%60));int amp=0;try{amp=recorder.getMaxAmplitude();}catch(Exception ignored){}float normalized=Math.max(0f,Math.min(1f,amp/32767f));System.arraycopy(recordLevels,1,recordLevels,0,recordLevels.length-1);recordLevels[recordLevels.length-1]=normalized;for(int i=0;i<wave.getChildCount()&&i<recordLevels.length;i++){View b=wave.getChildAt(i);int h=2+Math.round(recordLevels[i]*36f);ViewGroup.LayoutParams lp=b.getLayoutParams();lp.height=dp(Math.max(2,h));b.setLayoutParams(lp);}main.postDelayed(this,55);}};main.post(recordTicker);
    }
    private void showRecordingLocked(){if(recordBar==null)return;recordBar.setAlpha(1f);}
    private void stopRecorder(boolean send){if(recorder==null)return;long duration=System.currentTimeMillis()-recordStarted;boolean finalized=true;try{recorder.stop();}catch(Exception ex){finalized=false;}try{recorder.release();}catch(Exception ignored){}recorder=null;if(recordTicker!=null)main.removeCallbacks(recordTicker);if(recordOverlay!=null&&recordOverlay.getParent()!=null)((ViewGroup)recordOverlay.getParent()).removeView(recordOverlay);recordOverlay=null;if(composer!=null)composer.setAlpha(1f);File file=recordFile;recordFile=null;recordLocked=false;recordCanceled=false;recordLockReady=false;recordDeleteHot=false;recordLockHot=false;pendingMicStart=false;recordCancelButton=null;recordCancelHint=null;recordLockHint=null;recordLockIndicator=null;if(send&&finalized&&duration>=300&&file!=null&&file.exists())sendRecordedVoice(file,duration);else if(send&&duration>=300)toast("Voice recording could not be finalized.");else if(file!=null)file.delete();}
    private void sendRecordedVoice(File file,long duration){final String cid=activeConversation==null?"":activeConversation.optString("id");final String reply=replyTo==null?"":replyTo.optString("id");if(replyTo!=null)setReply(null);new Thread(()->{byte[] bytes=null;try{for(int i=0;i<8;i++){if(file.exists()&&file.length()>64){bytes=readAll(new FileInputStream(file));if(bytes.length>64)break;}Thread.sleep(80);}if(bytes==null||bytes.length<=64){main.post(()->toast("Voice recording is empty. Please try again."));file.delete();return;}String client="voice-native-"+UUID.randomUUID();byte[] payload=bytes;api.upload("/api/messaging/conversations/"+cid+"/attachment",payload,"voice-"+System.currentTimeMillis()+"-"+duration+"ms.m4a","audio/mp4","",client,reply,(json,error)->main.post(()->{try{file.delete();}catch(Exception ignored){}if(error!=null){toast(error.getMessage());return;}JSONObject m=json.optJSONObject("message");if(m!=null){upsertMessage(m);cacheMessagesNow();refreshInbox();}}));}catch(Exception e){try{file.delete();}catch(Exception ignored){}main.post(()->toast(e.getMessage()));}}).start();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);

        if(requestCode==REQ_CAMERA){
            if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)showMessageCamera();
            else toast("Camera permission is required.");
            return;
        }

        if(requestCode==REQ_MEDIA){
            if(hasInstagramMediaPermission()){
                showInstagramMediaPicker();
            }else{
                toast("Allow photo and video access to open your gallery.");
            }
            return;
        }

        if(requestCode==REQ_MIC){
            if(
                grantResults.length>0 &&
                grantResults[0]==PackageManager.PERMISSION_GRANTED &&
                pendingMicStart
            ){
                pendingMicStart=false;
                recordLocked=true;
                recordDownX=recordDownY=0;
                startRecorder();
                showRecordingLocked();
                vibrateSafe(18);
            }else{
                pendingMicStart=false;
            }
        }
    }

    private void showContacts(){root.removeAllViews();activeConversation=null;LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.WHITE);root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(8),0,dp(8),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(56)));ImageButton back=icon(R.drawable.ic_msg_back,40,TEXT);head.addView(back);back.setOnClickListener(v->showInbox(false));TextView t=text("New message",20,TEXT,Typeface.BOLD);head.addView(t,new LinearLayout.LayoutParams(0,-1,1));EditText q=new EditText(this);q.setHint("Search people");q.setSingleLine(true);q.setBackground(bg(LIGHT,22));q.setPadding(dp(15),0,dp(15),0);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(40));qp.setMargins(dp(12),dp(8),dp(12),dp(5));page.addView(q,qp);ListView contacts=new ListView(this);contacts.setDivider(null);page.addView(contacts,new LinearLayout.LayoutParams(-1,0,1));final List<JSONObject> data=new ArrayList<>();BaseAdapter a=new BaseAdapter(){public int getCount(){return data.size();}public Object getItem(int p){return data.get(p);}public long getItemId(int p){return p;}public View getView(int p,View cv,ViewGroup parent){JSONObject c=data.get(p);LinearLayout r=new LinearLayout(MainActivity.this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(6),dp(12),dp(6));View av=buildUserAvatar(c.optString("avatar"),c.optString("name"),48);r.addView(av,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView n=text(c.optString("name"),15,TEXT,Typeface.BOLD);LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(0,dp(60),1);nl.leftMargin=dp(10);r.addView(n,nl);return r;}};contacts.setAdapter(a);Runnable load=()->api.get("/api/messaging/contacts?q="+Uri.encode(q.getText().toString()),(json,error)->main.post(()->{if(error!=null)return;data.clear();JSONArray ar=json.optJSONArray("contacts");if(ar!=null)for(int i=0;i<ar.length();i++){JSONObject o=ar.optJSONObject(i);if(o!=null)data.add(o);}a.notifyDataSetChanged();}));load.run();q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){main.removeCallbacks(load);main.postDelayed(load,250);}public void afterTextChanged(Editable e){}});contacts.setOnItemClickListener((p,v,pos,id)->{JSONObject c=data.get(pos);try{api.post("/api/messaging/conversations",new JSONObject().put("type","direct").put("userId",c.optString("id")),(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject conv=json.optJSONObject("conversation");if(conv!=null)openConversation(conv);}));}catch(Exception e){toast(e.getMessage());}});}

    private void showInfo(){
        if(activeConversation==null)return;JSONObject c=activeConversation;root.removeAllViews();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(11,15,20));root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(7),0,dp(7),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(50)));ImageButton back=icon(R.drawable.ic_msg_back,38,Color.WHITE);back.setPadding(dp(9),dp(9),dp(9),dp(9));head.addView(back);back.setOnClickListener(v->openConversation(c));TextView title=text("",17,Color.WHITE,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-1,1));Space hs=new Space(this);head.addView(hs,new LinearLayout.LayoutParams(dp(38),dp(38)));
        ScrollView sc=new ScrollView(this);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(12),0,dp(24));sc.addView(body,new ScrollView.LayoutParams(-1,-2));
        View av=buildConversationAvatar(c,84);LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(dp(84),dp(84));avp.gravity=Gravity.CENTER_HORIZONTAL;body.addView(av,avp);TextView name=text(c.optString("name"),22,Color.rgb(245,245,247),Typeface.NORMAL);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,dp(47));nlp.topMargin=dp(12);body.addView(name,nlp);
        LinearLayout quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER);quick.setPadding(dp(13),0,dp(13),dp(19));body.addView(quick,new LinearLayout.LayoutParams(-1,dp(76)));
        boolean isGroup="group".equals(c.optString("type"));String[] ql={isGroup?"Add":"Profile","Search",c.optString("mutedUntil").isEmpty()?"Mute":"Unmute","Options"};int[] qi={isGroup?R.drawable.ic_info_add:R.drawable.ic_info_profile,R.drawable.ic_info_search,R.drawable.ic_info_mute,R.drawable.ic_info_more};
        for(int i=0;i<4;i++){LinearLayout q=new LinearLayout(this);q.setOrientation(LinearLayout.VERTICAL);q.setGravity(Gravity.CENTER);q.setClickable(true);q.setMinimumWidth(dp(68));q.setMinimumHeight(dp(70));int iconSize=i==3?46:31;ImageButton ib=icon(qi[i],iconSize,Color.WHITE);ib.setPadding(i==3?dp(2):0,i==3?dp(2):0,i==3?dp(2):0,i==3?dp(2):0);ib.setTranslationY(dp(8));ib.setClickable(false);LinearLayout.LayoutParams ibp=new LinearLayout.LayoutParams(dp(iconSize),dp(36));ibp.gravity=Gravity.CENTER_HORIZONTAL;q.addView(ib,ibp);TextView qt=text(ql[i],12,Color.WHITE,Typeface.NORMAL);qt.setGravity(Gravity.CENTER);LinearLayout.LayoutParams qtp=new LinearLayout.LayoutParams(-1,dp(28));q.addView(qt,qtp);quick.addView(q,new LinearLayout.LayoutParams(0,-1,1));final int ix=i;q.setOnClickListener(v->{if(ix==0&&isGroup)showAddPeopleToGroup(c);else if(ix==1)showConversationSearch(c);else if(ix==2)toggleMute(c);else if(ix==3)showInfoOptions(c,q);});}
        LinearLayout theme=infoRow(R.drawable.ic_info_theme,"Theme",themeLabel(c.optString("theme","default")),true);body.addView(theme);theme.setOnClickListener(v->showThemePage(c));
        LinearLayout nick=infoRow(R.drawable.ic_info_nick,"Nicknames","",false);body.addView(nick);nick.setOnClickListener(v->showNicknames(c));
        if("group".equals(c.optString("type"))){LinearLayout people=infoRow(R.drawable.ic_info_group,"People","",false);body.addView(people);people.setOnClickListener(v->showGroupPeople(c));}
        else{LinearLayout group=infoRow(R.drawable.ic_info_group,"Create a group chat","",false);body.addView(group);group.setOnClickListener(v->showCreateGroupFromActive(c));}
        TextView shared=text("Shared media",15,Color.WHITE,Typeface.BOLD);LinearLayout.LayoutParams shp=new LinearLayout.LayoutParams(-1,dp(43));shp.setMargins(dp(17),dp(12),dp(17),0);body.addView(shared,shp);addSharedMedia(body);
    }
    private LinearLayout infoRow(int drawable,String title,String subtitle,boolean theme){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(17),dp(5),dp(17),dp(5));row.setMinimumHeight(dp(57));ImageView iv=new ImageView(this);iv.setImageResource(drawable);iv.setColorFilter(theme?BLUE:Color.WHITE);iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);row.addView(iv,new LinearLayout.LayoutParams(dp(27),dp(27)));LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,dp(47),1);llp.leftMargin=dp(14);row.addView(labels,llp);labels.addView(text(title,16,Color.WHITE,Typeface.NORMAL),new LinearLayout.LayoutParams(-1,subtitle.isEmpty()?dp(47):dp(26)));if(!subtitle.isEmpty())labels.addView(text(subtitle,12,Color.rgb(174,179,187),Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(20)));TextView chevron=text("›",24,Color.rgb(174,179,187),Typeface.NORMAL);chevron.setGravity(Gravity.CENTER);row.addView(chevron,new LinearLayout.LayoutParams(dp(20),dp(47)));return row;}
    private void addSharedMedia(LinearLayout body){GridLayout grid=new GridLayout(this);grid.setColumnCount(3);grid.setPadding(0,0,0,0);int size=(getResources().getDisplayMetrics().widthPixels-dp(4))/3,count=0;for(JSONObject m:messages){JSONArray at=m.optJSONArray("attachments");if(at==null)continue;for(int i=0;i<at.length()&&count<9;i++){JSONObject a=at.optJSONObject(i);if(a==null)continue;String type=m.optString("type");if(!"image".equals(type)&&!"video".equals(type))continue;ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackgroundColor(Color.rgb(23,25,29));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=size;lp.height=size;lp.setMargins(dp(1),dp(1),dp(1),dp(1));grid.addView(im,lp);if("image".equals(type))images.load(a.optString("url"),im);else im.setImageResource(R.drawable.ic_msg_play);final JSONObject mediaMessage=m,mediaAttachment=a;final String mediaType=type;im.setOnClickListener(v->{if("video".equals(mediaType))showVideoMediaViewer(mediaMessage,mediaAttachment);else showMediaViewer(mediaMessage,mediaAttachment);});count++;}}if(count>0)body.addView(grid,new LinearLayout.LayoutParams(-1,-2));else{TextView empty=text("No shared photos or videos",13,Color.rgb(174,179,187),Typeface.NORMAL);empty.setGravity(Gravity.CENTER);body.addView(empty,new LinearLayout.LayoutParams(-1,dp(70)));}}
    private void showInfoOptions(JSONObject c,View anchor){final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout ov=new FrameLayout(this);ov.setBackgroundColor(Color.TRANSPARENT);LinearLayout menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(dp(5),dp(5),dp(5),dp(5));menu.setBackground(bg(Color.rgb(39,43,48),14));int[] loc=new int[2];anchor.getLocationOnScreen(loc);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(142),-2,Gravity.TOP|Gravity.START);mp.topMargin=loc[1]+anchor.getHeight()-dp(2);mp.leftMargin=Math.max(dp(8),Math.min(getResources().getDisplayMetrics().widthPixels-dp(150),loc[0]+anchor.getWidth()-dp(142)));ov.addView(menu,mp);boolean group="group".equals(c.optString("type"));LinearLayout action=infoOptionRow(group?R.drawable.ic_info_leave:R.drawable.ic_info_block,group?"Leave":(c.optBoolean("blockedByMe")?"Unblock":"Block"));menu.addView(action);action.setOnClickListener(v->{d.dismiss();toggleBlockOrLeave(c);});ov.setOnClickListener(v->{if(v==ov)d.dismiss();});d.setContentView(ov);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(0);w.setLayout(-1,-1);}}
    private LinearLayout infoOptionRow(int drawable,String label){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(10),0,dp(10),0);r.setMinimumHeight(dp(44));ImageView iv=new ImageView(this);iv.setImageResource(drawable);iv.setColorFilter(Color.rgb(255,82,104));r.addView(iv,new LinearLayout.LayoutParams(dp(21),dp(21)));TextView t=text(label,15,Color.rgb(255,82,104),Typeface.NORMAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(44),1);tp.leftMargin=dp(10);r.addView(t,tp);return r;}
    private void toggleMute(JSONObject c){try{api.patch("/api/messaging/conversations/"+c.optString("id")+"/settings",new JSONObject().put("muted",c.optString("mutedUntil").isEmpty()),(json,error)->main.post(()->{if(error!=null)toast(error.getMessage());else{JSONObject nc=json.optJSONObject("conversation");if(nc!=null)activeConversation=nc;showInfo();}}));}catch(Exception e){toast(e.getMessage());}}
    private LinearLayout darkSubPage(String titleText,Runnable backAction){root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(11,15,20));root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(7),0,dp(7),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(50)));ImageButton back=icon(R.drawable.ic_msg_back,38,Color.WHITE);back.setPadding(dp(9),dp(9),dp(9),dp(9));head.addView(back);back.setOnClickListener(v->backAction.run());TextView title=text(titleText,17,Color.WHITE,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-1,1));Space sp=new Space(this);head.addView(sp,new LinearLayout.LayoutParams(dp(38),dp(38)));return page;}
    private void showConversationSearch(JSONObject c){LinearLayout page=darkSubPage("Search",this::showInfo);LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setPadding(dp(16),dp(7),dp(16),0);page.addView(wrap,new LinearLayout.LayoutParams(-1,0,1));EditText q=new EditText(this);q.setHint("Search in conversation");q.setHintTextColor(Color.rgb(174,179,187));q.setTextColor(Color.WHITE);q.setSingleLine(true);q.setTextSize(16);q.setPadding(dp(16),0,dp(16),0);q.setBackground(bg(Color.rgb(36,40,46),22));wrap.addView(q,new LinearLayout.LayoutParams(-1,dp(43)));LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(this);scroll.addView(results,new ScrollView.LayoutParams(-1,-2));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,0,1);slp.topMargin=dp(8);wrap.addView(scroll,slp);final Runnable[] run=new Runnable[1];run[0]=()->{String query=q.getText().toString().trim();if(query.isEmpty()){results.removeAllViews();return;}api.get("/api/messaging/search?q="+Uri.encode(query)+"&conversationId="+c.optString("id"),(json,error)->main.post(()->{results.removeAllViews();if(error!=null){TextView e=text(error.getMessage(),13,Color.rgb(174,179,187),Typeface.NORMAL);e.setGravity(Gravity.CENTER);results.addView(e,new LinearLayout.LayoutParams(-1,dp(80)));return;}JSONArray a=json.optJSONArray("results");if(a==null||a.length()==0){TextView empty=text("No messages found",13,Color.rgb(174,179,187),Typeface.NORMAL);empty.setGravity(Gravity.CENTER);results.addView(empty,new LinearLayout.LayoutParams(-1,dp(80)));return;}for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(7),dp(10),dp(7),dp(10));TextView sn=text(senderName(m),15,Color.WHITE,Typeface.BOLD);row.addView(sn,new LinearLayout.LayoutParams(-1,dp(25)));TextView body=text(m.optString("body",previewForType(m.optString("type"))),13,Color.rgb(174,179,187),Typeface.NORMAL);body.setSingleLine(true);body.setEllipsize(TextUtils.TruncateAt.END);row.addView(body,new LinearLayout.LayoutParams(-1,dp(22)));results.addView(row,new LinearLayout.LayoutParams(-1,dp(58)));View div=new View(this);div.setBackgroundColor(Color.rgb(32,36,42));results.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));row.setOnClickListener(v->{String target=m.optString("id");openConversation(c);main.postDelayed(()->{int pos=-1;for(int x=0;x<messages.size();x++)if(target.equals(messages.get(x).optString("id"))){pos=x;break;}if(pos>=0&&list!=null){list.setSelection(Math.max(0,pos-2));}},260);});}}));};q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c1,int a){}public void onTextChanged(CharSequence cs,int st,int b,int c1){main.removeCallbacks(run[0]);main.postDelayed(run[0],240);}public void afterTextChanged(Editable e){}});q.requestFocus();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);}
    private String themeLabel(String key){String k=key==null?"default":key;String[] keys={"default","instagram","instagram-classic","love","ocean","sunset","monochrome","glow-pup","odyssey","supergirl","avatar","olivia","backrooms","deli-boys","heart-drive","valentines"};String[] labels={"Default","Instagram","Classic Instagram","Love","Ocean","Sunset","Monochrome","Glow Pup","The Odyssey","Supergirl","Avatar: The Last Airbender","Olivia Rodrigo","Backrooms","Deli Boys","Heart Drive","Valentine’s Day"};for(int i=0;i<keys.length;i++)if(keys[i].equals(k))return labels[i];return"Default";}
    private void showThemePage(JSONObject c){LinearLayout page=darkSubPage("Theme",this::showInfo);ScrollView scroll=new ScrollView(this);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));GridLayout grid=new GridLayout(this);grid.setColumnCount(3);grid.setPadding(dp(16),dp(12),dp(16),dp(28));scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));String[] keys={"default","instagram","instagram-classic","love","ocean","sunset","monochrome","glow-pup","odyssey","supergirl","avatar","olivia","backrooms","deli-boys","heart-drive","valentines"};int totalW=getResources().getDisplayMetrics().widthPixels-dp(56);int cardW=totalW/3;int previewH=Math.round(cardW/.69f);for(String key:keys){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(3),0,dp(3),dp(12));ThemePreviewView preview=new ThemePreviewView(this,key,key.equals(c.optString("theme","default")));card.addView(preview,new LinearLayout.LayoutParams(-1,previewH));TextView label=text(themeLabel(key),12,Color.WHITE,Typeface.NORMAL);label.setGravity(Gravity.TOP|Gravity.START);LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,dp(38));llp.topMargin=dp(8);card.addView(label,llp);GridLayout.LayoutParams cp=new GridLayout.LayoutParams();cp.width=cardW;cp.height=previewH+dp(58);cp.setMargins(dp(2),0,dp(2),dp(4));grid.addView(card,cp);card.setOnClickListener(v->{String previous=c.optString("theme","default");try{c.put("theme",key);activeConversation=c;}catch(Exception ignored){}showThemePage(c);try{api.patch("/api/messaging/conversations/"+c.optString("id")+"/theme",new JSONObject().put("theme",key),(json,error)->main.post(()->{if(error!=null){try{c.put("theme",previous);}catch(Exception ignored){}toast(error.getMessage());showThemePage(c);return;}JSONObject nc=json.optJSONObject("conversation");if(nc!=null)activeConversation=nc;}));}catch(Exception e){toast(e.getMessage());}});}}
    private void showNicknames(JSONObject c){LinearLayout page=darkSubPage("Nicknames",this::showInfo);ScrollView scroll=new ScrollView(this);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(4),dp(12),0);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));JSONArray ps=c.optJSONArray("participants");if(ps==null)return;for(int i=0;i<ps.length();i++){JSONObject person=ps.optJSONObject(i);if(person==null)continue;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(9),dp(6),dp(9),dp(6));row.setBackgroundColor(Color.TRANSPARENT);View av=buildUserAvatar(avatarUrl(person),person.optString("name"),46);row.addView(av,new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.leftMargin=dp(10);row.addView(labels,lp);String nick=person.optString("nickname");labels.addView(text(nick.isEmpty()?"Set nickname":nick,15,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(28)));labels.addView(text(person.optString("originalName",person.optString("name")),12,Color.rgb(174,179,187),Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(23)));TextView chev=text("›",22,Color.rgb(174,179,187),Typeface.NORMAL);chev.setGravity(Gravity.CENTER);row.addView(chev,new LinearLayout.LayoutParams(dp(28),dp(54)));body.addView(row,new LinearLayout.LayoutParams(-1,dp(66)));row.setOnClickListener(v->editNickname(c,person));}}
    private void editNickname(JSONObject c,JSONObject person){Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.argb(153,0,0,0));LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(bg(Color.rgb(39,43,48),18));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(340)),-2,Gravity.CENTER);overlay.addView(card,cp);card.addView(text("Set nickname",20,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(34)));EditText input=new EditText(this);input.setSingleLine(true);input.setMaxLines(1);input.setText(person.optString("nickname"));input.setHint(person.optString("originalName",person.optString("name")));input.setHintTextColor(Color.rgb(174,179,187));input.setTextColor(Color.WHITE);input.setPadding(dp(12),0,dp(12),0);input.setBackground(bg(Color.rgb(23,25,29),10));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(44));ip.topMargin=dp(6);card.addView(input,ip);LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END);LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,dp(48));alp.topMargin=dp(10);card.addView(actions,alp);Button cancel=new Button(this);cancel.setText("Cancel");cancel.setTextColor(Color.WHITE);cancel.setAllCaps(false);cancel.setBackground(bg(Color.rgb(58,63,69),9));actions.addView(cancel,new LinearLayout.LayoutParams(dp(88),dp(39)));Button save=new Button(this);save.setText("Save");save.setTextColor(Color.WHITE);save.setAllCaps(false);save.setBackground(bg(Color.rgb(124,92,255),9));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(78),dp(39));sp.leftMargin=dp(8);actions.addView(save,sp);cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->{String value=input.getText().toString().trim(),old=person.optString("nickname");try{person.put("nickname",value);}catch(Exception ignored){}d.dismiss();showNicknames(c);try{api.patch("/api/messaging/conversations/"+c.optString("id")+"/nicknames/"+person.optString("id"),new JSONObject().put("nickname",value),(json,error)->main.post(()->{if(error!=null){try{person.put("nickname",old);}catch(Exception ignored){}toast(error.getMessage());showNicknames(c);return;}JSONObject nc=json.optJSONObject("conversation");if(nc!=null)activeConversation=nc;}));}catch(Exception e){toast(e.getMessage());}});d.setContentView(overlay);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(0);w.setLayout(-1,-1);}input.requestFocus();input.setSelection(input.length());}
    private void showCreateGroupFromActive(JSONObject c){Set<String> selected=new HashSet<>();JSONArray existing=c.optJSONArray("participants");if(existing!=null)for(int i=0;i<existing.length();i++){JSONObject p=existing.optJSONObject(i);if(p!=null&&!p.optBoolean("isSelf"))selected.add(p.optString("id"));}root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.WHITE);root.addView(page,new FrameLayout.LayoutParams(-1,-1));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(8),0,dp(8),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(56)));ImageButton back=icon(R.drawable.ic_msg_back,40,TEXT);head.addView(back);back.setOnClickListener(v->showInfo());TextView title=text("New group",20,TEXT,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-1,1));EditText q=new EditText(this);q.setHint("Search people");q.setSingleLine(true);q.setPadding(dp(15),0,dp(15),0);q.setBackground(bg(LIGHT,22));LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(40));qp.setMargins(dp(12),dp(4),dp(12),dp(5));page.addView(q,qp);FrameLayout content=new FrameLayout(this);page.addView(content,new LinearLayout.LayoutParams(-1,0,1));ListView contacts=new ListView(this);contacts.setDivider(null);content.addView(contacts,new FrameLayout.LayoutParams(-1,-1));Button create=new Button(this);create.setAllCaps(false);create.setTextColor(Color.WHITE);create.setTextSize(14);create.setBackground(bg(BLUE,10));FrameLayout.LayoutParams cfp=new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM);cfp.setMargins(dp(12),dp(8),dp(12),dp(10));content.addView(create,cfp);final List<JSONObject> data=new ArrayList<>();BaseAdapter a=new BaseAdapter(){public int getCount(){return data.size();}public Object getItem(int p){return data.get(p);}public long getItemId(int p){return p;}public View getView(int pos,View cv,ViewGroup parent){JSONObject person=data.get(pos);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(6),dp(12),dp(6));View av=buildUserAvatar(person.optString("avatar"),person.optString("name"),48);row.addView(av,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout labels=new LinearLayout(MainActivity.this);labels.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(60),1);lp.leftMargin=dp(10);row.addView(labels,lp);labels.addView(text(person.optString("name"),15,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(30)));String sub=person.optBoolean("isFriend")?"Friend":person.optBoolean("online")?"Active now":"";labels.addView(text(sub,12,SUB,Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(24)));GroupSelectView check=new GroupSelectView(MainActivity.this,selected.contains(person.optString("id")));row.addView(check,new LinearLayout.LayoutParams(dp(28),dp(28)));return row;}};contacts.setAdapter(a);Runnable syncCreate=()->{create.setText("Create group ("+selected.size()+")");create.setVisibility(selected.isEmpty()?View.GONE:View.VISIBLE);};syncCreate.run();Runnable load=()->api.get("/api/messaging/contacts?q="+Uri.encode(q.getText().toString()),(json,error)->main.post(()->{if(error!=null)return;data.clear();JSONArray ar=json.optJSONArray("contacts");if(ar!=null)for(int i=0;i<ar.length();i++){JSONObject o=ar.optJSONObject(i);if(o!=null)data.add(o);}a.notifyDataSetChanged();syncCreate.run();}));load.run();q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c1,int a1){}public void onTextChanged(CharSequence cs,int st,int b,int c1){main.removeCallbacks(load);main.postDelayed(load,250);}public void afterTextChanged(Editable e){}});contacts.setOnItemClickListener((pp,v,pos,id)->{String uid=data.get(pos).optString("id");if(selected.contains(uid))selected.remove(uid);else selected.add(uid);a.notifyDataSetChanged();syncCreate.run();});create.setOnClickListener(v->{if(selected.isEmpty())return;try{JSONArray ids=new JSONArray();for(String id:selected)ids.put(id);JSONObject req=new JSONObject().put("type","group").put("title","").put("memberIds",ids);api.post("/api/messaging/conversations",req,(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject conv=json.optJSONObject("conversation");if(conv!=null)openConversation(conv);}));}catch(Exception e){toast(e.getMessage());}});}
    private void showGroupPeople(JSONObject c){
        root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(11,15,20));root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(7),0,dp(7),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(49)));ImageButton back=icon(R.drawable.ic_msg_back,38,Color.WHITE);head.addView(back);back.setOnClickListener(v->showInfo());TextView title=text("People",18,Color.WHITE,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-1,1));head.addView(new Space(this),new LinearLayout.LayoutParams(dp(38),dp(38)));
        ScrollView scroll=new ScrollView(this);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(3),0,dp(24));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        LinearLayout add=infoRow(R.drawable.ic_info_add,"Add people","",false);body.addView(add);add.setOnClickListener(v->showAddPeopleToGroup(c));
        JSONObject self=null;List<JSONObject> others=new ArrayList<>();JSONArray ps=c.optJSONArray("participants");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject person=ps.optJSONObject(i);if(person==null)continue;if(person.optBoolean("isSelf"))self=person;else others.add(person);}TextView yh=text("You",17,Color.WHITE,Typeface.BOLD);yh.setPadding(dp(20),0,0,0);LinearLayout.LayoutParams yhp=new LinearLayout.LayoutParams(-1,dp(42));yhp.topMargin=dp(7);body.addView(yh,yhp);if(self!=null)body.addView(groupPersonRow(self));TextView ph=text("People ("+others.size()+")",17,Color.WHITE,Typeface.BOLD);ph.setPadding(dp(20),0,0,0);LinearLayout.LayoutParams php=new LinearLayout.LayoutParams(-1,dp(42));php.topMargin=dp(6);body.addView(ph,php);for(JSONObject person:others)body.addView(groupPersonRow(person));
    }
    private View groupPersonRow(JSONObject person){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(7),dp(18),dp(7));row.setMinimumHeight(dp(67));row.addView(buildUserAvatar(person.optString("avatar"),person.optString("name"),49),new LinearLayout.LayoutParams(dp(49),dp(49)));LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(55),1);lp.leftMargin=dp(9);row.addView(labels,lp);labels.addView(text(person.optString("name"),15,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(28)));String sub=("admin".equals(person.optString("role"))?"Admin · ":"")+person.optString("originalName");labels.addView(text(sub,12,Color.rgb(174,179,187),Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(22)));return row;}

    private void showAddPeopleToGroup(JSONObject c){
        root.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(11,15,20));root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(7),0,dp(10),0);page.addView(head,new LinearLayout.LayoutParams(-1,dp(50)));ImageButton back=icon(R.drawable.ic_msg_back,38,Color.WHITE);head.addView(back);back.setOnClickListener(v->showGroupPeople(c));TextView title=text("Add people",18,Color.WHITE,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-1,1));Button done=new Button(this);done.setText("Done");done.setAllCaps(false);done.setTextSize(15);done.setTypeface(Typeface.DEFAULT,Typeface.BOLD);done.setTextColor(Color.rgb(78,119,255));done.setBackgroundColor(Color.TRANSPARENT);done.setVisibility(View.INVISIBLE);head.addView(done,new LinearLayout.LayoutParams(dp(70),dp(44)));
        ListView contacts=new ListView(this);contacts.setDivider(null);page.addView(contacts,new LinearLayout.LayoutParams(-1,0,1));final List<JSONObject> data=new ArrayList<>();final Set<String> selected=new HashSet<>();Set<String> existing=new HashSet<>();JSONArray ps=c.optJSONArray("participants");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject x=ps.optJSONObject(i);if(x!=null)existing.add(x.optString("id"));}
        BaseAdapter a=new BaseAdapter(){public int getCount(){return data.size();}public Object getItem(int p){return data.get(p);}public long getItemId(int p){return p;}public View getView(int pos,View cv,ViewGroup parent){JSONObject person=data.get(pos);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(7),dp(18),dp(7));row.addView(buildUserAvatar(person.optString("avatar"),person.optString("name"),49),new LinearLayout.LayoutParams(dp(49),dp(49)));TextView n=text(person.optString("name"),15,Color.WHITE,Typeface.BOLD);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(55),1);np.leftMargin=dp(9);row.addView(n,np);GroupSelectView mark=new GroupSelectView(MainActivity.this,selected.contains(person.optString("id")));row.addView(mark,new LinearLayout.LayoutParams(dp(24),dp(24)));return row;}};contacts.setAdapter(a);
        contacts.setOnItemClickListener((pp,v,pos,id)->{String uid=data.get(pos).optString("id");if(selected.contains(uid))selected.remove(uid);else selected.add(uid);a.notifyDataSetChanged();done.setVisibility(selected.isEmpty()?View.INVISIBLE:View.VISIBLE);});
        done.setOnClickListener(v->{if(selected.isEmpty())return;done.setEnabled(false);addSelectedMembers(c,new ArrayList<>(selected),0,done);});
        api.get("/api/messaging/contacts",(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONArray ar=json.optJSONArray("contacts");data.clear();if(ar!=null)for(int i=0;i<ar.length();i++){JSONObject x=ar.optJSONObject(i);if(x!=null&&!existing.contains(x.optString("id")))data.add(x);}a.notifyDataSetChanged();}));
    }
    private void addSelectedMembers(JSONObject c,List<String> ids,int index,Button done){if(index>=ids.size()){done.setEnabled(true);showGroupPeople(activeConversation==null?c:activeConversation);return;}try{api.post("/api/messaging/conversations/"+c.optString("id")+"/members",new JSONObject().put("userId",ids.get(index)),(json,error)->main.post(()->{if(error!=null){done.setEnabled(true);toast(error.getMessage());return;}JSONObject nc=json.optJSONObject("conversation");if(nc!=null)activeConversation=nc;addSelectedMembers(activeConversation==null?c:activeConversation,ids,index+1,done);}));}catch(Exception e){done.setEnabled(true);toast(e.getMessage());}}


    private void toggleBlockOrLeave(JSONObject c){if("group".equals(c.optString("type"))){api.delete("/api/messaging/conversations/"+c.optString("id")+"/leave",(json,error)->main.post(()->{if(error!=null)toast(error.getMessage());else showInbox(false);}));return;}try{api.post("/api/messaging/conversations/"+c.optString("id")+"/block",new JSONObject().put("blocked",!c.optBoolean("blockedByMe")),(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject nc=json.optJSONObject("conversation");if(nc!=null)activeConversation=nc;showInfo();}));}catch(Exception e){toast(e.getMessage());}}

    private void connectSocket(){
        if(socket!=null||socketConnecting||api==null||!api.hasSession())return;
        socketConnecting=true;
        socket=api.openMessengerSocket(json->main.post(()->handleSocket(json)),()->main.post(()->{
            socket=null;socketConnecting=false;
            main.removeCallbacks(socketReconnect);
            long delay=Math.min(8000,500L*(1L<<Math.min(socketRetry++,4)));
            main.postDelayed(socketReconnect,delay);
        }));
    }
    private void queueActiveSocketMessage(String cid,JSONObject message){if(message==null)return;if(!socketMessageBatchConversationId.isEmpty()&&!socketMessageBatchConversationId.equals(cid))flushSocketMessageBatch();socketMessageBatchConversationId=cid;socketMessageBatch.add(message);main.removeCallbacks(flushSocketMessages);main.postDelayed(flushSocketMessages,180);}
    private void flushSocketMessageBatch(){if(socketMessageBatch.isEmpty())return;String cid=socketMessageBatchConversationId;List<JSONObject> incoming=new ArrayList<>(socketMessageBatch);socketMessageBatch.clear();socketMessageBatchConversationId="";if(activeConversation==null||!activeConversation.optString("id").equals(cid))return;boolean keepBottom=isConversationAtBottom(),refreshNeeded=false;for(JSONObject message:incoming){JSONObject old=findPreviousMessage(messages,message);if(old!=null){boolean visualConfirmation=old.optBoolean("pending")&&isVisualMediaMessage(old);int index=messages.indexOf(old);JSONObject merged=mergeMessage(old,message);preserveLocalMediaPreview(old,merged);messages.set(index,merged);try{messages.get(index).put("pending",false).put("optimisticRetainUntil",System.currentTimeMillis()+30000L);}catch(Exception ignored){}if(visualConfirmation)hidePendingIndicator(old.optString("clientId"));else refreshNeeded=true;}else if(!message.optBoolean("deleted")){messages.add(message);refreshNeeded=true;}}prefetchTemporaryMedia();if(refreshNeeded)scheduleMessageRefresh(keepBottom);cacheMessagesNow();markRead();}
    private void handleSocket(JSONObject e){String type=e.optString("type");
        if("ready".equals(type)){selfId=e.optString("userId");socketConnecting=false;socketRetry=0;main.removeCallbacks(socketReconnect);refreshInbox();if(activeConversation!=null){refreshMessages(activeConversation.optString("id"));markRead();}return;}
        if("conversation".equals(type)||"conversation_update".equals(type)){JSONObject c=e.optJSONObject("conversation");if(c!=null&&activeConversation!=null&&c.optString("id").equals(activeConversation.optString("id")))activeConversation=c;refreshInbox();return;}
        if("message".equals(type)||"message_update".equals(type)){JSONObject m=e.optJSONObject("message");String cid=e.optString("conversationId",m==null?"":m.optString("conversationId"));if(m!=null){if(isStickerMessage(m))stickerLastConversations.add(cid);else if("message".equals(type))stickerLastConversations.remove(cid);}if("message".equals(type)&&m!=null&&!cid.isEmpty())updateInboxFromIncomingMessage(cid,m);if(activeConversation!=null&&activeConversation.optString("id").equals(cid)&&m!=null)queueActiveSocketMessage(cid,m);else if(m!=null&&!cid.isEmpty())cacheIncomingMessage(cid,m,"message".equals(type));refreshInbox();return;}
        if("message_hidden".equals(type)){String id=e.optString("messageId");messages.removeIf(m->id.equals(m.optString("id")));if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();return;}
        if("typing".equals(type)){String tcid=e.optString("conversationId");boolean active=e.optBoolean("active")&&!selfId.equals(e.optString("userId"));Runnable previous=typingExpiry.remove(tcid);if(previous!=null)main.removeCallbacks(previous);if(active){typingConversations.add(tcid);Runnable expire=()->{typingExpiry.remove(tcid);typingConversations.remove(tcid);if(inboxAdapter!=null)inboxAdapter.notifyDataSetChanged();if(activeConversation!=null&&activeConversation.optString("id").equals(tcid)&&typingView!=null){typingView.setText("");typingView.setVisibility(View.GONE);}};typingExpiry.put(tcid,expire);main.postDelayed(expire,2400);}else{typingConversations.remove(tcid);}if(inboxAdapter!=null)inboxAdapter.notifyDataSetChanged();if(activeConversation!=null&&activeConversation.optString("id").equals(tcid)&&typingView!=null){typingView.setText(active?"Typing..":"");typingView.setVisibility(active?View.VISIBLE:View.GONE);}return;}
        if("read".equals(type)){
            if(activeConversation!=null&&activeConversation.optString("id").equals(e.optString("conversationId"))){
                long id=parseLong(e.optString("messageId"));
                String seenAt=e.optString("seenAt",e.optString("readAt",e.optString("at","")));
                if(seenAt.isEmpty())seenAt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date());
                for(JSONObject m:messages){
                    if(isMine(m)&&parseLong(m.optString("id"))<=id){
                        try{m.put("status","read").put("seenAt",seenAt);}catch(Exception ignored){}
                    }
                }
                if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();
            }
            return;
        }
        if("presence".equals(type)){refreshInbox();}
    }
    private void queueTypingPulse(){if(activeConversation==null)return;boolean has=messageInput!=null&&messageInput.length()>0;if(!has){main.removeCallbacks(typingPulse);main.removeCallbacks(stopTyping);if(typingStateSent)sendTyping(false);return;}if(!typingStateSent)sendTyping(true);main.removeCallbacks(typingPulse);main.postDelayed(typingPulse,350);main.removeCallbacks(stopTyping);main.postDelayed(stopTyping,1800);}
    private final Runnable typingPulse=new Runnable(){public void run(){if(activeConversation!=null&&messageInput!=null&&messageInput.length()>0){sendTyping(true);main.postDelayed(this,350);}}};
    private final Runnable startTyping=()->{};
    private void sendTyping(boolean active){if(activeConversation==null)return;if(socket==null){connectSocket();return;}long now=SystemClock.uptimeMillis();if(active&&now-lastTypingWireAt<300)return;try{boolean ok=socket.send(new JSONObject().put("type","typing").put("conversationId",activeConversation.optString("id")).put("active",active).toString());if(ok){typingStateSent=active;lastTypingWireAt=now;}else{socket=null;connectSocket();}}catch(Exception ignored){socket=null;connectSocket();}}
    private final Runnable stopTyping=()->{main.removeCallbacks(typingPulse);sendTyping(false);};
    private void markRead(){if(activeConversation==null)return;JSONObject last=null;for(int i=messages.size()-1;i>=0;i--)if(!messages.get(i).optString("id").isEmpty()&&!messages.get(i).optString("id").startsWith("tmp-")){last=messages.get(i);break;}if(last==null)return;final String cid=activeConversation.optString("id"),mid=last.optString("id");markConversationReadLocally(cid);try{api.post("/api/messaging/conversations/"+cid+"/read",new JSONObject().put("messageId",mid),(j,e)->main.post(()->{if(e==null)refreshInbox();}));}catch(Exception ignored){}}

    private String conversationStatus(JSONObject c){JSONArray ps=c.optJSONArray("participants");int others=0;JSONObject other=null;if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&!p.optBoolean("isSelf")){others++;if(other==null)other=p;}}if("group".equals(c.optString("type"))||others>1)return (ps==null?others+1:ps.length())+" people";if(other!=null&&other.optBoolean("online"))return"Active now";Date last=parseDate(other==null?null:other.optString("lastSeenAt"));if(last==null)return"";long sec=Math.max(0,(System.currentTimeMillis()-last.getTime())/1000);if(sec<60)return"Active now";long min=sec/60;if(min<60)return"Active "+min+"m ago";long h=min/60;if(h<24)return"Active "+h+"h ago";long days=h/24;return"Active "+days+" "+(days==1?"day":"days")+" ago";}
    private Date parseDate(String value){if(value==null||value.isEmpty())return null;String v=value.replace("Z","+00:00");String[] patterns={"yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"};for(String p:patterns)try{return new SimpleDateFormat(p,Locale.US).parse(v);}catch(Exception ignored){}return null;}
    private boolean sameDay(Date a,Date b){if(a==null||b==null)return false;Calendar x=Calendar.getInstance(),y=Calendar.getInstance();x.setTime(a);y.setTime(b);return x.get(Calendar.YEAR)==y.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==y.get(Calendar.DAY_OF_YEAR);}
    private String inboxTime(String value){
        Date d=parseDate(value);
        if(d==null)return "";

        long diff=Math.max(
            0,
            System.currentTimeMillis()-d.getTime()
        );

        if(diff<60000)return "now";

        long minutes=diff/60000;
        if(minutes<60)return minutes+"m";

        long hours=minutes/60;
        if(hours<24)return hours+"h";

        long days=hours/24;
        if(days<7)return days+"d";

        return new SimpleDateFormat(
            "MMM d",
            Locale.getDefault()
        ).format(d);
    }
    private String clusterStamp(String value){
        Date d=parseDate(value);
        if(d==null)return"";
        String time=new SimpleDateFormat("h:mm a",Locale.getDefault()).format(d);
        Date now=new Date();
        if(sameDay(d,now))return time;

        Calendar yesterday=Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR,-1);
        Calendar dc=Calendar.getInstance();
        dc.setTime(d);
        if(
            yesterday.get(Calendar.YEAR)==dc.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR)==dc.get(Calendar.DAY_OF_YEAR)
        ){
            return "YESTERDAY "+time;
        }

        return new SimpleDateFormat("MMM d",Locale.getDefault())
            .format(d)
            .toUpperCase(Locale.getDefault())+", "+time;
    }
    private boolean sameBurst(JSONObject a,JSONObject b){if(a==null||b==null||isMine(a)!=isMine(b)||!a.optString("senderId").equals(b.optString("senderId")))return false;Date x=parseDate(a.optString("createdAt")),y=parseDate(b.optString("createdAt"));return x!=null&&y!=null&&sameDay(x,y)&&y.getTime()-x.getTime()>=0&&y.getTime()-x.getTime()<=BURST_MS;}
    private boolean needsStamp(int p){if(p<0||p>=messages.size())return false;JSONObject cur=messages.get(p);if("system".equals(cur.optString("type")))return false;Date b=parseDate(cur.optString("createdAt"));if(b==null)return false;for(int i=p-1;i>=0;i--){JSONObject prev=messages.get(i);if("system".equals(prev.optString("type")))continue;Date a=parseDate(prev.optString("createdAt"));if(a==null)continue;return !sameDay(a,b)||b.getTime()-a.getTime()>=STAMP_MS;}return true;}
    private boolean isMine(JSONObject m){if(m==null)return false;if(!selfId.isEmpty()&&selfId.equals(m.optString("senderId")))return true;if(activeConversation!=null){JSONArray ps=activeConversation.optJSONArray("participants");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&p.optBoolean("isSelf")&&p.optString("id").equals(m.optString("senderId")))return true;}}return false;}
    private String status(JSONObject m){
        if(m==null)return"";
        if(m.optBoolean("pending"))return"";

        String state=m.optString("status","sent");

        if("read".equals(state)){
            String at=m.optString("seenAt",m.optString("readAt",""));
            Date seen=parseDate(at);
            long seenMs;

            if(seen!=null){
                seenMs=seen.getTime();
            }else{
                String key=m.optString("id",m.optString("clientId"));
                Long remembered=localSeenAt.get(key);
                if(remembered==null){
                    remembered=System.currentTimeMillis();
                    if(key!=null&&!key.isEmpty())localSeenAt.put(key,remembered);
                }
                seenMs=remembered;
            }

            long diff=Math.max(0,System.currentTimeMillis()-seenMs);
            if(diff<60000)return"Seen";

            long minutes=diff/60000;
            if(minutes<60)return"Seen "+minutes+"m ago";

            long hours=minutes/60;
            if(hours<24)return"Seen "+hours+"h ago";

            long days=hours/24;
            return"Seen "+days+"d ago";
        }

        if("delivered".equals(state))return"Delivered";
        if("failed".equals(state))return"";
        return"Sent";
    }
    private String firstLetter(String name){String n=name==null?"":name.trim();return n.isEmpty()?"?":String.valueOf(Character.toUpperCase(n.charAt(0)));}
    private String avatarUrl(JSONObject o){if(o==null)return"";String[] keys={"avatar","groupAvatar","photoUrl","imageUrl","picture","profilePicture","avatarUrl"};for(String k:keys){String v=o.optString(k);if(v!=null&&!v.isEmpty()&&!"null".equalsIgnoreCase(v))return v;}return"";}
    private JSONObject firstOther(JSONObject c){JSONArray ps=c==null?null:c.optJSONArray("participants");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&!p.optBoolean("isSelf"))return p;}return null;}
    private View buildUserAvatar(String url,String name,int size){
        FrameLayout box=new FrameLayout(this);
        box.setBackground(bg(Color.TRANSPARENT,size/2f));
        box.setClipToOutline(true);
        box.setElevation(0f);

        ImageView fallback=new ImageView(this);
        fallback.setImageResource(R.drawable.default_profile);
        fallback.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fallback.setBackgroundColor(Color.TRANSPARENT);
        fallback.setScaleX(1.14f);
        fallback.setScaleY(1.14f);
        box.addView(fallback,new FrameLayout.LayoutParams(dp(size),dp(size)));

        if(url!=null&&!url.isEmpty()&&!"null".equalsIgnoreCase(url)){
            ImageView im=new ImageView(this);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            im.setBackgroundColor(Color.TRANSPARENT);
            im.setScaleX(1.085f);
            im.setScaleY(1.085f);
            box.addView(im,new FrameLayout.LayoutParams(dp(size),dp(size)));
            images.load(url,im);
        }
        return box;
    }
    private View buildConversationAvatar(JSONObject c,int size){String own=avatarUrl(c);if(!own.isEmpty())return buildUserAvatar(own,c.optString("name"),size);JSONArray ps=c.optJSONArray("participants");if("group".equals(c.optString("type"))&&ps!=null&&ps.length()>1){FrameLayout f=new FrameLayout(this);int child=Math.round(size*.7f);int made=0;for(int i=0;i<ps.length()&&made<2;i++){JSONObject p=ps.optJSONObject(i);if(p==null)continue;View av=buildUserAvatar(avatarUrl(p),p.optString("name"),child);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(child),dp(child),made==0?Gravity.START|Gravity.TOP:Gravity.END|Gravity.BOTTOM);f.addView(av,lp);made++;}return f;}JSONObject other=firstOther(c);return buildUserAvatar(avatarUrl(other),c.optString("name"),size);}
    private String senderAvatar(JSONObject m){JSONObject s=m.optJSONObject("sender");String u=avatarUrl(s);if(!u.isEmpty())return u;JSONObject other=firstOther(activeConversation);return avatarUrl(other);}
    private String senderName(JSONObject m){JSONObject s=m.optJSONObject("sender");return s==null?"Facebook user":s.optString("name","Facebook user");}
    private String previewForType(String t){if("image".equals(t))return"📷 Photo";if("video".equals(t))return"🎬 Video";if("audio".equals(t))return"🎤 Voice message";if("file".equals(t))return"📎 File";if("shared_reel".equals(t))return"Shared a reel";if("shared_post".equals(t))return"Shared a post";return"";}
    private void toast(String s){Toast.makeText(this,s==null||s.isEmpty()?"Something went wrong":s,Toast.LENGTH_SHORT).show();}

    private boolean isStickerMessage(JSONObject m){if(m==null)return false;if(m.optBoolean("sticker"))return true;JSONArray at=m.optJSONArray("attachments");if(at==null||at.length()==0)return false;JSONObject a=at.optJSONObject(0);if(a==null)return false;String mime=a.optString("mime",a.optString("mimeType",a.optString("type",""))).toLowerCase(Locale.ROOT);String name=a.optString("name","").toLowerCase(Locale.ROOT);String url=a.optString("url","").toLowerCase(Locale.ROOT);return a.optBoolean("sticker")||mime.contains("gif")||name.endsWith(".gif")||name.startsWith("sticker-")||url.contains("giphy.com");}
    private boolean isKnownLastSticker(JSONObject c,JSONObject last){if(c==null||last==null)return false;if(isStickerMessage(last))return true;String cid=c.optString("id");if(stickerLastConversations.contains(cid))return true;try{String raw=cache.get("messages:"+cid);if(raw!=null){JSONArray arr=new JSONObject(raw).optJSONArray("messages");if(arr!=null&&arr.length()>0){JSONObject lm=arr.optJSONObject(arr.length()-1);String lid=last.optString("id");if(lm!=null&&(lid.isEmpty()||lid.equals(lm.optString("id")))&&isStickerMessage(lm))return true;}}}catch(Exception ignored){}return false;}
    private final class InboxAdapter extends BaseAdapter{
        public int getCount(){return filteredInbox.size();}
        public Object getItem(int p){return filteredInbox.get(p);}
        public long getItemId(int p){return p;}

        public View getView(int p,View cv,ViewGroup parent){
            JSONObject c=filteredInbox.get(p);
            int unread=Math.max(0,c.optInt("unread"));

            LinearLayout row=new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8),dp(7),dp(8),dp(7));
            row.setMinimumHeight(dp(72));

            FrameLayout avWrap=new FrameLayout(MainActivity.this);
            View av=buildConversationAvatar(c,56);
            avWrap.addView(av,new FrameLayout.LayoutParams(dp(56),dp(56)));

            JSONObject other=firstOther(c);
            if(!"group".equals(c.optString("type"))&&other!=null&&other.optBoolean("online")){
                FrameLayout activeOuter=new FrameLayout(MainActivity.this);
                activeOuter.setBackground(bg(Color.WHITE,9));

                FrameLayout.LayoutParams outerLp=
                    new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.END|Gravity.BOTTOM);
                outerLp.setMargins(0,0,0,0);
                avWrap.addView(activeOuter,outerLp);

                View activeInner=new View(MainActivity.this);
                activeInner.setBackground(bg(Color.rgb(44,203,93),7));

                FrameLayout.LayoutParams innerLp=
                    new FrameLayout.LayoutParams(dp(11),dp(11),Gravity.CENTER);
                activeOuter.addView(activeInner,innerLp);
            }

            row.addView(avWrap,new LinearLayout.LayoutParams(dp(56),dp(56)));

            LinearLayout copy=new LinearLayout(MainActivity.this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(58),1);
            cp.leftMargin=dp(10);
            row.addView(copy,cp);

            LinearLayout nameRow=new LinearLayout(MainActivity.this);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            copy.addView(nameRow,new LinearLayout.LayoutParams(-1,dp(27)));

            TextView name=text(
                c.optString("name","Conversation"),
                15.5f,
                TEXT,
                unread>0?Typeface.BOLD:Typeface.NORMAL
            );
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            nameRow.addView(name,new LinearLayout.LayoutParams(-2,-1));

            if(c.optBoolean("pinned")){
                ImageView pin=new ImageView(MainActivity.this);
                pin.setImageResource(R.drawable.messenger_pin);
                pin.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                LinearLayout.LayoutParams pp=
                    new LinearLayout.LayoutParams(dp(16),dp(16));
                pp.leftMargin=dp(4);
                nameRow.addView(pin,pp);
            }

            JSONObject last=c.optJSONObject("lastMessage");
            boolean isTyping=typingConversations.contains(c.optString("id"));

            String pv;

            if(isTyping){
                pv="Typing..";
            }else if(unread>=2){
                pv=unread+" new messages";
            }else{
                pv=last==null?"No messages yet":last.optString("body");

                if(last!=null&&isKnownLastSticker(c,last)){
                    pv="Sent a sticker";
                }else if(last!=null&&(pv==null||pv.isEmpty())){
                    pv=previewForType(last.optString("type"));
                }
            }

            if(!isTyping&&last!=null&&!last.optString("createdAt").isEmpty()){
                String relative=inboxTime(last.optString("createdAt"));
                if(!relative.isEmpty())pv=pv+" · "+relative;
            }

            TextView preview=text(
                pv,
                13.5f,
                isTyping?BLUE:(unread>0?TEXT:SUB),
                isTyping?Typeface.BOLD:(unread>0?Typeface.BOLD:Typeface.NORMAL)
            );
            preview.setSingleLine(true);
            preview.setEllipsize(TextUtils.TruncateAt.END);

            copy.addView(
                preview,
                new LinearLayout.LayoutParams(-1,dp(26))
            );

            LinearLayout meta=new LinearLayout(MainActivity.this);
            meta.setGravity(Gravity.CENTER);
            row.addView(meta,new LinearLayout.LayoutParams(dp(34),dp(58)));

            if(unread>0){
                View unreadDot=new View(MainActivity.this);
                unreadDot.setBackground(bg(Color.rgb(0,149,246),4));
                meta.addView(
                    unreadDot,
                    new LinearLayout.LayoutParams(dp(8),dp(8))
                );
            }

            wireConversationLongPress(row,c);
            return row;
        }
    }
    private Dialog threadSheetDialog;
    private FrameLayout threadSheetOverlay;
    private LinearLayout threadSheetCard;
    private float threadSheetDragStart=Float.NaN;

    private void wireConversationLongPress(View row,JSONObject c){
        row.setClickable(true);
        row.setLongClickable(false);

        final float[] downX={0f},downY={0f};
        final boolean[] moved={false},opened={false};
        final Runnable[] hold={null};

        row.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX[0]=e.getRawX();
                    downY[0]=e.getRawY();
                    moved[0]=false;
                    opened[0]=false;

                    hold[0]=()->{
                        if(moved[0])return;
                        opened[0]=true;
                        showThreadActions(c,downY[0]);
                    };
                    main.postDelayed(hold[0],165);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-downX[0];
                    float dy=e.getRawY()-downY[0];

                    if(!opened[0]&&(Math.abs(dx)>dp(9)||Math.abs(dy)>dp(9))){
                        moved[0]=true;
                        if(hold[0]!=null)main.removeCallbacks(hold[0]);
                    }

                    if(opened[0]){
                        updateThreadSheetDrag(e.getRawY());
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(hold[0]!=null)main.removeCallbacks(hold[0]);

                    if(opened[0]){
                        finishThreadSheetDrag();
                        opened[0]=false;
                        return true;
                    }

                    if(e.getActionMasked()==MotionEvent.ACTION_UP&&!moved[0]){
                        openConversation(c);
                    }
                    return true;
            }
            return true;
        });
    }

    private void updateThreadSheetDrag(float rawY){
        if(threadSheetCard==null||Float.isNaN(threadSheetDragStart))return;

        float dy=Math.max(0,rawY-threadSheetDragStart);
        threadSheetCard.setTranslationY(dy);

        if(threadSheetOverlay!=null){
            float denom=Math.max(dp(260),threadSheetCard.getHeight()*.75f);
            float ratio=Math.min(1f,dy/denom);
            threadSheetOverlay.setBackgroundColor(
                Color.argb((int)(170*(1f-ratio)),0,0,0)
            );
        }
    }

    private void finishThreadSheetDrag(){
        if(threadSheetCard==null)return;

        float y=threadSheetCard.getTranslationY();
        threadSheetDragStart=Float.NaN;

        if(y>dp(86)){
            LinearLayout card=threadSheetCard;
            Dialog dialog=threadSheetDialog;

            card.animate()
                .translationY(Math.max(card.getHeight(),dp(420)))
                .setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(()->{
                    if(dialog!=null&&dialog.isShowing())dialog.dismiss();
                    clearThreadSheetRefs();
                })
                .start();
        }else{
            threadSheetCard.animate()
                .translationY(0)
                .setDuration(210)
                .setInterpolator(new DecelerateInterpolator())
                .start();

            if(threadSheetOverlay!=null){
                threadSheetOverlay.setBackgroundColor(Color.argb(170,0,0,0));
            }
        }
    }

    private void clearThreadSheetRefs(){
        threadSheetDialog=null;
        threadSheetOverlay=null;
        threadSheetCard=null;
        threadSheetDragStart=Float.NaN;
    }

    private boolean conversationIsMuted(JSONObject c){
        if(c==null)return false;
        if(c.optBoolean("muted"))return true;

        String until=c.optString("mutedUntil","");
        if(until==null)return false;
        until=until.trim();
        if(until.isEmpty()||"null".equalsIgnoreCase(until)||"false".equalsIgnoreCase(until))return false;

        Date d=parseDate(until);
        if(d!=null)return d.getTime()>System.currentTimeMillis();

        // Some backends use a non-date sentinel only while muted.
        return !"0".equals(until);
    }

    private void showThreadActions(JSONObject c,float initialFingerY){
        final Dialog d=new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout overlay=new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(170,0,0,0));

        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0,dp(9),0,dp(14));
        card.setBackground(topBg(Color.rgb(23,28,33),28));

        FrameLayout.LayoutParams cp=
            new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        overlay.addView(card,cp);

        View handle=new View(this);
        handle.setBackground(bg(Color.rgb(155,161,170),3));

        LinearLayout.LayoutParams hp=
            new LinearLayout.LayoutParams(dp(55),dp(4));
        hp.gravity=Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin=dp(18);
        card.addView(handle,hp);

        TextView title=text(
            c.optString("name"),
            17,
            Color.WHITE,
            Typeface.BOLD
        );
        title.setPadding(dp(22),0,dp(22),0);
        card.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));

        View div=new View(this);
        div.setBackgroundColor(Color.rgb(48,52,58));
        card.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));

        LinearLayout pin=threadActionRow(
            R.drawable.ic_thread_pin,
            c.optBoolean("pinned")?"Unpin":"Pin",
            false
        );
        LinearLayout del=threadActionRow(
            R.drawable.ic_thread_trash,
            "Delete",
            true
        );
        LinearLayout mute=threadActionRow(
            R.drawable.ic_info_mute,
            conversationIsMuted(c)
                ?"Unmute messages"
                :"Mute messages",
            false
        );

        card.addView(pin);
        card.addView(del);
        card.addView(mute);

        pin.setOnClickListener(v->{
            d.dismiss();
            clearThreadSheetRefs();
            runThreadAction(c,"pin");
        });

        del.setOnClickListener(v->{
            d.dismiss();
            clearThreadSheetRefs();
            runThreadAction(c,"delete");
        });

        mute.setOnClickListener(v->{
            d.dismiss();
            clearThreadSheetRefs();
            runThreadAction(c,"mute");
        });

        overlay.setOnClickListener(v->{
            if(v==overlay){
                d.dismiss();
                clearThreadSheetRefs();
            }
        });

        View.OnTouchListener sheetDrag=(v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    threadSheetDragStart=e.getRawY()-card.getTranslationY();
                    card.animate().cancel();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    updateThreadSheetDrag(e.getRawY());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    finishThreadSheetDrag();
                    return true;
            }
            return true;
        };

        handle.setOnTouchListener(sheetDrag);
        title.setOnTouchListener(sheetDrag);

        card.setOnTouchListener((v,e)->{
            if(
                e.getActionMasked()==MotionEvent.ACTION_DOWN &&
                e.getY()>dp(72)
            ){
                return false;
            }
            return sheetDrag.onTouch(v,e);
        });

        d.setContentView(overlay);
        d.setOnDismissListener(dialog->clearThreadSheetRefs());
        d.show();

        Window w=d.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setDimAmount(0);
            w.setLayout(-1,-1);
            w.setGravity(Gravity.BOTTOM);
        }

        threadSheetDialog=d;
        threadSheetOverlay=overlay;
        threadSheetCard=card;
        threadSheetDragStart=initialFingerY;

        card.setTranslationY(dp(14));
        card.animate()
            .translationY(0)
            .setDuration(170)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }
    private LinearLayout threadActionRow(int iconRes,String label,boolean danger){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(22),0,dp(22),0);row.setMinimumHeight(dp(55));ImageView iv=new ImageView(this);iv.setImageResource(iconRes);int col=danger?Color.rgb(255,64,92):Color.WHITE;iv.setColorFilter(col);row.addView(iv,new LinearLayout.LayoutParams(dp(22),dp(22)));TextView t=text(label,16,col,Typeface.NORMAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(55),1);tp.leftMargin=dp(14);row.addView(t,tp);return row;}
    private void runThreadAction(JSONObject c,String action){try{if("delete".equals(action)){api.patch("/api/messaging/conversations/"+c.optString("id")+"/settings",new JSONObject().put("archived",true),(json,error)->main.post(()->{if(error!=null)toast(error.getMessage());else{inbox.removeIf(x->x.optString("id").equals(c.optString("id")));filterInbox(searchBox==null?"":searchBox.getText().toString());toast("Conversation deleted");}}));return;}boolean on="pin".equals(action)?!c.optBoolean("pinned"):!conversationIsMuted(c);JSONObject req=new JSONObject();if("pin".equals(action))req.put("pinned",on);else req.put("muted",on);api.patch("/api/messaging/conversations/"+c.optString("id")+"/settings",req,(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONObject nc=json.optJSONObject("conversation");if(nc!=null){for(int i=0;i<inbox.size();i++)if(inbox.get(i).optString("id").equals(nc.optString("id"))){inbox.set(i,nc);break;}}filterInbox(searchBox==null?"":searchBox.getText().toString());toast("pin".equals(action)?(on?"Pinned":"Unpinned"):(on?"Messages muted":"Messages unmuted"));}));}catch(Exception e){toast(e.getMessage());}}

    private int themeReplyBackground(){switch(activeTheme()){case"instagram":return Color.rgb(19,19,19);case"monochrome":return Color.rgb(214,214,214);case"glow-pup":return Color.rgb(52,43,105);case"odyssey":return Color.rgb(36,85,90);case"supergirl":return Color.rgb(82,43,38);case"avatar":return Color.rgb(52,85,80);case"olivia":return Color.rgb(86,64,76);case"backrooms":return Color.rgb(81,76,41);case"deli-boys":return Color.rgb(61,57,52);case"heart-drive":return Color.rgb(50,27,112);case"valentines":return Color.rgb(73,18,118);default:return Color.rgb(223,225,229);}}
    private int themeReplyText(){switch(activeTheme()){case"instagram":return Color.rgb(133,133,133);case"glow-pup":return Color.rgb(238,233,255);case"odyssey":return Color.rgb(227,255,255);case"supergirl":return Color.rgb(255,240,228);case"avatar":return Color.rgb(234,255,248);case"olivia":return Color.rgb(255,230,239);case"backrooms":return Color.rgb(255,251,216);case"deli-boys":return Color.rgb(255,244,233);case"heart-drive":return Color.rgb(238,231,255);case"valentines":return Color.rgb(243,229,255);default:return Color.rgb(75,79,86);}}

    private final class OlderMessagesSpinner extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF oval=new android.graphics.RectF();
        private boolean running=false;

        OlderMessagesSpinner(Context context){
            super(context);
            setWillNotDraw(false);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(2.1f));
        }

        void start(){
            running=true;
            setVisibility(View.VISIBLE);
            invalidate();
        }

        void stop(){
            running=false;
            setVisibility(View.INVISIBLE);
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(!running)return;

            float inset=dp(3.2f);
            oval.set(
                inset,
                inset,
                getWidth()-inset,
                getHeight()-inset
            );

            float cx=getWidth()/2f;
            float cy=getHeight()/2f;

            android.graphics.SweepGradient sweep=
                new android.graphics.SweepGradient(
                    cx,
                    cy,
                    new int[]{
                        Color.argb(18,145,148,154),
                        Color.argb(90,145,148,154),
                        Color.argb(220,145,148,154),
                        Color.argb(255,145,148,154),
                        Color.argb(55,145,148,154),
                        Color.argb(18,145,148,154)
                    },
                    new float[]{0f,.18f,.48f,.70f,.90f,1f}
                );

            paint.setShader(sweep);

            float angle=(SystemClock.uptimeMillis()%850L)/850f*360f;
            canvas.save();
            canvas.rotate(angle,cx,cy);
            canvas.drawArc(oval,0f,330f,false,paint);
            canvas.restore();

            paint.setShader(null);
            postInvalidateDelayed(16);
        }
    }

    private final class ReplyProgressView extends View{
        private final Paint trackPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress=0f;

        ReplyProgressView(Context context){
            super(context);
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);

            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            int replyGray=Color.rgb(139,142,148);

            trackPaint.setStrokeWidth(dp(1.8f));
            trackPaint.setColor(Color.argb(92,139,142,148));

            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(dp(2.5f));
            progressPaint.setColor(replyGray);

            setAlpha(0f);
        }

        void setProgress(float value){
            progress=Math.max(0f,Math.min(1f,value));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float inset=dp(2.1f);
            android.graphics.RectF oval=new android.graphics.RectF(
                inset,
                inset,
                getWidth()-inset,
                getHeight()-inset
            );

            canvas.drawArc(oval,-90f,360f,false,trackPaint);
            if(progress>0f){
                canvas.drawArc(
                    oval,
                    -90f,
                    360f*progress,
                    false,
                    progressPaint
                );
            }
        }
    }

    private final class TimedProgressView extends View{
        private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress=1f;
        TimedProgressView(Context context){super(context);track.setColor(Color.rgb(102,104,110));fill.setColor(Color.WHITE);track.setStrokeCap(Paint.Cap.ROUND);fill.setStrokeCap(Paint.Cap.ROUND);track.setStrokeWidth(dp(2));fill.setStrokeWidth(dp(2));}
        void setProgress(float value){progress=Math.max(0f,Math.min(1f,value));invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float y=getHeight()/2f;canvas.drawLine(0,y,getWidth(),y,track);canvas.drawLine(0,y,getWidth()*progress,y,fill);}
    }

    private static final class PausableClock{
        private final long started=SystemClock.uptimeMillis();
        private long pausedAt=0L,pausedTotal=0L;
        private boolean paused=false;
        void pause(){if(paused)return;paused=true;pausedAt=SystemClock.uptimeMillis();}
        void resume(){if(!paused)return;pausedTotal+=Math.max(0L,SystemClock.uptimeMillis()-pausedAt);paused=false;pausedAt=0L;}
        float fraction(long durationMs){long now=paused?pausedAt:SystemClock.uptimeMillis();return Math.max(0f,(now-started-pausedTotal)/(float)Math.max(1L,durationMs));}
    }

    private void wireTemporaryMediaHold(View target,Runnable pause,Runnable resume){final float[] downX={0f},downY={0f};final boolean[] holding={false};final Runnable[] trigger={null};target.setOnTouchListener((v,event)->{switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:downX[0]=event.getRawX();downY[0]=event.getRawY();holding[0]=false;trigger[0]=()->{holding[0]=true;if(pause!=null)pause.run();};main.postDelayed(trigger[0],190);break;case MotionEvent.ACTION_MOVE:if(!holding[0]&&(Math.abs(event.getRawX()-downX[0])>dp(9)||Math.abs(event.getRawY()-downY[0])>dp(9))&&trigger[0]!=null)main.removeCallbacks(trigger[0]);break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(trigger[0]!=null)main.removeCallbacks(trigger[0]);if(holding[0]&&resume!=null)resume.run();holding[0]=false;break;}return false;});}

    private String revealMessageTime(JSONObject m){
        if(m==null)return"";
        Date d=parseDate(m.optString("createdAt"));
        if(d==null)return"";
        return new SimpleDateFormat("h:mm a",Locale.getDefault()).format(d);
    }

    private void applyConversationTimeReveal(){
        if(list==null)return;
        for(int i=0;i<list.getChildCount();i++){
            applyConversationTimeRevealToTree(list.getChildAt(i));
        }
    }

    private void applyConversationTimeRevealToTree(View v){
        if(v==null)return;

        Object tag=v.getTag();
        if(tag instanceof Object[]){
            Object[] parts=(Object[])tag;
            if(parts.length==2&&parts[0] instanceof View&&parts[1] instanceof View){
                View moving=(View)parts[0];
                View time=(View)parts[1];

                float edgeTravel=
                    timeRevealOffset<=0f
                        ?0f
                        :timeRevealOffset+dp(10);
                moving.setTranslationX(-edgeTravel);

                float max=dp(58);
                float progress=max<=0f?0f:
                    Math.max(0f,Math.min(1f,timeRevealOffset/max));

                time.setAlpha(progress);
                time.setTranslationX(dp(16)*(1f-progress));
            }
        }

        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++){
                applyConversationTimeRevealToTree(g.getChildAt(i));
            }
        }
    }

    private void resetConversationTimeReveal(){
        final float start=timeRevealOffset;

        if(start<=0f){
            timeRevealOffset=0f;
            applyConversationTimeReveal();
            return;
        }

        final long started=SystemClock.uptimeMillis();
        final long duration=145L;

        Runnable anim=new Runnable(){
            @Override public void run(){
                float t=Math.min(
                    1f,
                    (SystemClock.uptimeMillis()-started)/(float)duration
                );
                float eased=1f-(1f-t)*(1f-t);

                timeRevealOffset=start*(1f-eased);
                applyConversationTimeReveal();

                if(t<1f)main.postDelayed(this,16);
                else{
                    timeRevealOffset=0f;
                    applyConversationTimeReveal();
                }
            }
        };

        main.post(anim);
    }

    private void wireConversationTimeRevealGesture(){
        if(list==null)return;

        list.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    timeRevealOffset=0f;
                    applyConversationTimeReveal();
                    timeRevealDownX=e.getRawX();
                    timeRevealDownY=e.getRawY();
                    timeRevealDragging=false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if(Float.isNaN(timeRevealDownX))return false;

                    float dx=e.getRawX()-timeRevealDownX;
                    float dy=e.getRawY()-timeRevealDownY;
                    float ax=Math.abs(dx),ay=Math.abs(dy);

                    if(!timeRevealDragging){
                        if(ay>dp(9)&&ay>ax*1.15f){
                            timeRevealDownX=Float.NaN;
                            timeRevealOffset=0f;
                            applyConversationTimeReveal();
                            return false;
                        }
                        if(dx<-dp(18)&&ax>ay*1.8f){
                            timeRevealDragging=true;

                            ViewParent parent=v.getParent();
                            if(parent!=null){
                                parent.requestDisallowInterceptTouchEvent(true);
                            }
                        }else{
                            return false;
                        }
                    }

                    timeRevealOffset=
                        Math.max(0f,Math.min(-dx,dp(58)));
                    applyConversationTimeReveal();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    timeRevealDownX=Float.NaN;
                    timeRevealDownY=Float.NaN;

                    if(timeRevealDragging){
                        timeRevealDragging=false;

                        ViewParent parent=v.getParent();
                        if(parent!=null){
                            parent.requestDisallowInterceptTouchEvent(false);
                        }

                        resetConversationTimeReveal();
                        return true;
                    }

                    return false;
            }

            return false;
        });
    }

    private JSONObject mediaStackAttachment(JSONObject message){JSONArray attachments=message==null?null:message.optJSONArray("attachments");return attachments!=null&&attachments.length()>0?attachments.optJSONObject(0):null;}
    private boolean isMediaStackCandidate(JSONObject message){JSONObject attachment=mediaStackAttachment(message);if(attachment==null||mediaViewMode(message,attachment)>0||isStickerMessage(message))return false;String type=actualMediaType(message.optString("type"),attachment);return "image".equals(type)||"video".equals(type);}
    private String mediaStackSender(JSONObject message){if(isMine(message))return "self";String senderId=message.optString("senderId","");JSONObject sender=message.optJSONObject("sender");if(senderId.isEmpty()&&sender!=null)senderId=sender.optString("id","");return senderId.isEmpty()?senderName(message):senderId;}
    private boolean sameMediaStack(JSONObject previous,JSONObject current){if(!isMediaStackCandidate(previous)||!isMediaStackCandidate(current)||!mediaStackSender(previous).equals(mediaStackSender(current)))return false;Date a=parseDate(previous.optString("createdAt")),b=parseDate(current.optString("createdAt"));return a!=null&&b!=null&&Math.abs(b.getTime()-a.getTime())<=90000L;}
    private View buildMediaStackRow(int start,int end){JSONObject last=messages.get(end);boolean mine=isMine(last);LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);if(needsStamp(start)){TextView stamp=text(clusterStamp(messages.get(start).optString("createdAt")),11,Color.rgb(138,141,145),Typeface.NORMAL);stamp.setGravity(Gravity.CENTER);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(31));slp.topMargin=dp(7);slp.bottomMargin=dp(2);outer.addView(stamp,slp);}LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.END|Gravity.BOTTOM:Gravity.START|Gravity.BOTTOM);LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.leftMargin=dp(10);rowLp.rightMargin=dp(10);rowLp.topMargin=dp(4);rowLp.bottomMargin=dp(5);outer.addView(row,rowLp);if(!mine){View avatar=buildUserAvatar(senderAvatar(last),senderName(last),30);LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(dp(30),dp(30));avatarLp.rightMargin=dp(6);row.addView(avatar,avatarLp);}LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(mine?Gravity.END:Gravity.START);row.addView(column,new LinearLayout.LayoutParams(-2,-2));int count=end-start+1,photos=0,videos=0;for(int i=start;i<=end;i++){JSONObject attachment=mediaStackAttachment(messages.get(i));String type=actualMediaType(messages.get(i).optString("type"),attachment);if("video".equals(type))videos++;else photos++;}String kind=photos>0&&videos>0?"photos and videos":videos==count?(count==1?"video":"videos"):(count==1?"photo":"photos");TextView label=text((mine?"You":senderName(last))+" sent "+count+" "+kind,12.5f,Color.rgb(138,141,145),Typeface.NORMAL);label.setGravity(mine?Gravity.END:Gravity.START);LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(dp(196),dp(27));column.addView(label,labelLp);FrameLayout deck=new FrameLayout(this);column.addView(deck,new LinearLayout.LayoutParams(dp(196),dp(181)));int shown=Math.min(3,count),first=end-shown+1;for(int i=first;i<=end;i++){int layer=i-first;JSONObject message=messages.get(i),attachment=mediaStackAttachment(message);String type=actualMediaType(message.optString("type"),attachment);FrameLayout card=new FrameLayout(this);card.setPadding(dp(3),dp(3),dp(3),dp(3));card.setBackground(bg(Color.WHITE,14));card.setElevation(dp(2+layer));FrameLayout.LayoutParams cardLp=new FrameLayout.LayoutParams(dp(148),dp(158));int shift=layer*10;if(mine)cardLp.leftMargin=dp(24+shift);else cardLp.leftMargin=dp(4+shift);cardLp.topMargin=dp(12-layer*3);deck.addView(card,cardLp);card.setRotation(layer==shown-1?0f:(layer%2==0?-7f:6f));ImageView media=new ImageView(this);media.setScaleType(ImageView.ScaleType.CENTER_CROP);media.setBackground(bg(Color.rgb(20,20,20),11));media.setClipToOutline(true);FrameLayout.LayoutParams mediaLp=new FrameLayout.LayoutParams(dp(142),dp(152));card.addView(media,mediaLp);if("image".equals(type))images.load(displayMediaUrl(attachment),media);else{String poster=attachment.optString("thumbnailUrl",attachment.optString("previewUrl",attachment.optString("posterUrl","")));if(!poster.isEmpty())images.load(poster,media);TextView play=text("▶",27,Color.WHITE,Typeface.BOLD);play.setGravity(Gravity.CENTER);play.setShadowLayer(dp(2),0,dp(1),Color.BLACK);card.addView(play,new FrameLayout.LayoutParams(-1,-1));}card.setOnClickListener(v->showMediaStackGallery(start,end));}deck.setOnClickListener(v->showMediaStackGallery(start,end));boolean pending=false;for(int i=start;i<=end;i++)if(messages.get(i).optBoolean("pending")){pending=true;break;}if(pending){TextView sending=text("Sending…",10.5f,Color.rgb(138,141,145),Typeface.NORMAL);sending.setGravity(mine?Gravity.END:Gravity.START);column.addView(sending,new LinearLayout.LayoutParams(dp(196),dp(18)));}else if(mine&&end==messages.size()-1){String state=status(last);if(!state.isEmpty()){TextView sent=text(state,11.5f,Color.rgb(138,141,145),Typeface.NORMAL);sent.setGravity(Gravity.END);column.addView(sent,new LinearLayout.LayoutParams(dp(196),dp(19)));}}deck.setOnLongClickListener(v->{showMessageActions(last,deck);return true;});return outer;}

    private final class MessageAdapter extends BaseAdapter{
        private final List<int[]> rows=new ArrayList<>();
        private int rowMessageCount=-1;
        private void rebuildRows(){rows.clear();for(int start=0;start<messages.size();){int end=start;if(isMediaStackCandidate(messages.get(start)))while(end+1<messages.size()&&sameMediaStack(messages.get(end),messages.get(end+1)))end++;rows.add(new int[]{start,end});start=end+1;}rowMessageCount=messages.size();}
        private void ensureRows(){if(rowMessageCount!=messages.size())rebuildRows();}
        @Override public void notifyDataSetChanged(){rebuildRows();super.notifyDataSetChanged();}
        int messageIndexForRow(int row){ensureRows();return row>=0&&row<rows.size()?rows.get(row)[0]:-1;}
        int messageEndForRow(int row){ensureRows();return row>=0&&row<rows.size()?rows.get(row)[1]:-1;}
        int rowForMessageIndex(int index){ensureRows();for(int i=0;i<rows.size();i++)if(index>=rows.get(i)[0]&&index<=rows.get(i)[1])return i;return Math.max(0,Math.min(index,rows.size()-1));}
        public int getCount(){ensureRows();return rows.size();}public Object getItem(int p){int index=messageIndexForRow(p);return index>=0?messages.get(index):null;}public boolean hasStableIds(){return true;}public long getItemId(int p){int start=messageIndexForRow(p),end=messageEndForRow(p);if(start<0)return p;JSONObject message=messages.get(start);String key=message.optString("clientId","");if(key.isEmpty())key=message.optString("id",String.valueOf(start));if(end>start)key+="|"+messages.get(end).optString("id",String.valueOf(end));return key.hashCode()&0xffffffffL;}
        private int lastMineIndex(){for(int i=messages.size()-1;i>=0;i--)if(isMine(messages.get(i))&&!messages.get(i).optBoolean("pending"))return i;return-1;}
        public View getView(int rowPosition,View cv,ViewGroup parent){int p=messageIndexForRow(rowPosition),groupEnd=messageEndForRow(rowPosition);if(p<0)return new Space(MainActivity.this);if(groupEnd>p)return buildMediaStackRow(p,groupEnd);JSONObject m=messages.get(p);LinearLayout outer=new LinearLayout(MainActivity.this);outer.setOrientation(LinearLayout.VERTICAL);if(needsStamp(p)){TextView stamp=text(clusterStamp(m.optString("createdAt")),11,Color.rgb(138,141,145),Typeface.NORMAL);stamp.setGravity(Gravity.CENTER);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(31));slp.topMargin=dp(7);slp.bottomMargin=dp(2);outer.addView(stamp,slp);}if("system".equals(m.optString("type"))){TextView sys=text(m.optString("body"),12,Color.rgb(138,141,145),Typeface.NORMAL);sys.setGravity(Gravity.CENTER);sys.setPadding(dp(25),dp(8),dp(25),dp(8));outer.addView(sys,new LinearLayout.LayoutParams(-1,-2));return outer;}boolean mine=isMine(m),samePrev=p>0&&sameBurst(messages.get(p-1),m),sameNext=p+1<messages.size()&&sameBurst(m,messages.get(p+1));

            FrameLayout swipeHost=new FrameLayout(MainActivity.this);
            swipeHost.setClipChildren(false);
            swipeHost.setClipToPadding(false);
            outer.setClipChildren(false);
            outer.setClipToPadding(false);
            LinearLayout.LayoutParams swipeHostLp=new LinearLayout.LayoutParams(-1,-2);
            swipeHostLp.leftMargin=dp(10);
            swipeHostLp.rightMargin=mine?dp(4):dp(10);
            swipeHostLp.topMargin=dp(samePrev?1:3);
            swipeHostLp.bottomMargin=dp(sameNext?1:3);
            outer.addView(swipeHost,swipeHostLp);

            ReplyProgressView replyProgress=
                new ReplyProgressView(MainActivity.this);

            ImageView replyArrow=new ImageView(MainActivity.this);
            replyArrow.setImageResource(R.drawable.ic_msg_reply);
            replyArrow.setColorFilter(Color.rgb(139,142,148));
            replyArrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            replyArrow.setAlpha(0f);
            replyArrow.setScaleX(.78f);
            replyArrow.setScaleY(.78f);
            replyArrow.setBackgroundColor(Color.TRANSPARENT);
            replyArrow.setPadding(dp(1.75f),dp(1.75f),dp(1.75f),dp(1.75f));

            FrameLayout.LayoutParams arrowLp=
                new FrameLayout.LayoutParams(dp(27),dp(27),mine
                    ?Gravity.END|Gravity.CENTER_VERTICAL
                    :Gravity.START|Gravity.CENTER_VERTICAL);
            arrowLp.leftMargin=mine?0:dp(35);
            arrowLp.rightMargin=mine?dp(2):0;

            FrameLayout.LayoutParams progressLp=
                new FrameLayout.LayoutParams(dp(30),dp(30),mine
                    ?Gravity.END|Gravity.CENTER_VERTICAL
                    :Gravity.START|Gravity.CENTER_VERTICAL);
            progressLp.leftMargin=mine?0:dp(33);
            progressLp.rightMargin=mine?dp(0):0;

            swipeHost.addView(replyArrow,arrowLp);
            swipeHost.addView(replyProgress,progressLp);

            replyArrow.setTranslationX(0f);
            replyProgress.setTranslationX(0f);

            TextView sideTime=text(
                revealMessageTime(m),
                12.5f,
                Color.rgb(138,141,145),
                Typeface.NORMAL
            );
            sideTime.setSingleLine(true);
            sideTime.setAlpha(0f);
            sideTime.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);

            FrameLayout.LayoutParams sideTimeLp=
                new FrameLayout.LayoutParams(
                    dp(54),
                    -1,
                    Gravity.END|Gravity.CENTER_VERTICAL
                );
            sideTimeLp.rightMargin=dp(3);
            swipeHost.addView(sideTime,sideTimeLp);

            LinearLayout row=new LinearLayout(MainActivity.this);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setGravity(mine?Gravity.END|Gravity.BOTTOM:Gravity.START|Gravity.BOTTOM);
            swipeHost.addView(row,new FrameLayout.LayoutParams(-1,-2));

            swipeHost.setTag(new Object[]{row,sideTime});
            applyConversationTimeRevealToTree(swipeHost);

            replyArrow.bringToFront();
            replyProgress.bringToFront();

            if(!mine){
                View av=buildUserAvatar(senderAvatar(m),senderName(m),30);
                LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(dp(30),dp(30));
                alp.rightMargin=dp(5);
                if(samePrev)av.setVisibility(View.INVISIBLE);
                row.addView(av,alp);
            }

            LinearLayout stack=new LinearLayout(MainActivity.this);
            stack.setClipChildren(false);
            stack.setClipToPadding(false);
            stack.setOrientation(LinearLayout.VERTICAL);
            stack.setGravity(mine?Gravity.END:Gravity.START);
            row.addView(stack,new LinearLayout.LayoutParams(-2,-2));

            if(mine&&m.optBoolean("pending")){
                ImageView pendingIcon=new ImageView(MainActivity.this);
                pendingIcon.setImageResource(R.drawable.ic_msg_send);
                pendingIcon.setColorFilter(Color.rgb(145,148,154));
                pendingIcon.setAlpha(1f);
                pendingIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                pendingIcon.setPadding(0,0,0,0);
                pendingIcon.setRotation(0f);
                pendingIcon.setTranslationY(dp(2));
                pendingIcon.setTag("pending-send-"+m.optString("clientId"));

                LinearLayout.LayoutParams pendingLp=
                    new LinearLayout.LayoutParams(dp(16),dp(16));
                pendingLp.leftMargin=dp(3);
                row.addView(pendingIcon,pendingLp);
            }

            JSONObject reply=m.optJSONObject("reply");
            if(reply!=null){
                LinearLayout replyBlock=new LinearLayout(MainActivity.this);
                replyBlock.setOrientation(LinearLayout.VERTICAL);
                replyBlock.setGravity(mine?Gravity.END:Gravity.START);

                TextView repliedLabel=text(
                    mine?"You replied":"Replied to you",
                    12.5f,
                    "instagram".equals(activeTheme())?Color.rgb(133,133,133):Color.rgb(138,141,145),
                    Typeface.NORMAL
                );
                LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(-2,dp(24));
                if(mine)labelLp.rightMargin=dp(12);
                else labelLp.leftMargin=dp(12);
                replyBlock.addView(repliedLabel,labelLp);

                LinearLayout previewRow=new LinearLayout(MainActivity.this);
                previewRow.setGravity(Gravity.CENTER_VERTICAL);

                LinearLayout rpWrap=new LinearLayout(MainActivity.this);
                rpWrap.setOrientation(LinearLayout.VERTICAL);
                rpWrap.setGravity(Gravity.START);
                int replyBg=themeReplyBackground();
                int replyText=themeReplyText();
                rpWrap.setBackground(bg(replyBg,18));

                TextView rp=text(replyPreviewFromReply(reply),12,replyText,Typeface.NORMAL);
                rp.setSingleLine(true);
                rp.setEllipsize(TextUtils.TruncateAt.END);
                rp.setPadding(dp(12),dp(8),dp(12),dp(8));
                rp.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*.64f));
                rpWrap.addView(rp,new LinearLayout.LayoutParams(-2,-2));

                View replyLine=new View(MainActivity.this);
                replyLine.setBackground(bg(
                    "instagram".equals(activeTheme())?Color.rgb(38,38,38):Color.rgb(180,184,190),
                    2
                ));
                LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(dp(3),dp(46));

                if(mine){
                    previewRow.addView(rpWrap,new LinearLayout.LayoutParams(-2,-2));
                    lineLp.leftMargin=dp(8);
                    previewRow.addView(replyLine,lineLp);
                }else{
                    lineLp.rightMargin=dp(8);
                    previewRow.addView(replyLine,lineLp);
                    previewRow.addView(rpWrap,new LinearLayout.LayoutParams(-2,-2));
                }

                String replyId=reply.optString("id",reply.optString("messageId"));
                if(!replyId.isEmpty()){
                    rpWrap.setClickable(true);
                    rpWrap.setOnClickListener(v->jumpToMessage(replyId));
                    previewRow.setClickable(true);
                    previewRow.setOnClickListener(v->jumpToMessage(replyId));
                }

                replyBlock.addView(previewRow,new LinearLayout.LayoutParams(-2,-2));

                LinearLayout.LayoutParams rpp=new LinearLayout.LayoutParams(-2,-2);
                rpp.bottomMargin=dp(5);
                rpp.gravity=mine?Gravity.END:Gravity.START;
                stack.addView(replyBlock,rpp);
            }
            View content=buildMessageContent(m,mine,samePrev,sameNext);if(content.getLayoutParams()==null)content.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));stack.addView(content);

            if(reply!=null){
                // Align reply UI and timestamp with the actual message bubble,
                // not the combined reply-preview + message block.
                replyArrow.setTranslationY(dp(18));
                replyProgress.setTranslationY(dp(18));
                sideTime.setTranslationY(dp(19));
            }

            if(isActuallyEdited(m)){TextView ed=text("edited",9,mine?Color.rgb(220,232,255):Color.rgb(110,113,117),Typeface.NORMAL);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,dp(14));ep.gravity=mine?Gravity.END:Gravity.START;ep.leftMargin=dp(4);ep.rightMargin=dp(4);stack.addView(ed,ep);}JSONArray reactions=m.optJSONArray("reactions");if(reactions!=null&&reactions.length()>0){StringBuilder r=new StringBuilder();for(int i=0;i<reactions.length();i++){JSONObject rr=reactions.optJSONObject(i);if(rr!=null)r.append(rr.optString("emoji"));}if(reactions.length()>1)r.append(" ").append(reactions.length());TextView badge=text(r.toString(),12,TEXT,Typeface.NORMAL);badge.setGravity(Gravity.CENTER);badge.setPadding(reactions.length()==1?0:dp(5),0,reactions.length()==1?0:dp(5),0);badge.setBackground(bg("instagram".equals(activeTheme())?Color.rgb(38,38,38):Color.WHITE,11));badge.setElevation(0f);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(reactions.length()==1?dp(22):-2,dp(22));bp.gravity=mine?Gravity.END:Gravity.START;bp.topMargin=-dp(4);stack.addView(badge,bp);badge.setOnClickListener(v->showReactionDetails(m));}
            wireMessageGesture(content,content,m,mine,replyArrow,replyProgress);if(mine&&p==lastMineIndex()&&p==messages.size()-1&&!m.optBoolean("pending")){String statusText=status(m);if(!statusText.isEmpty()){TextView st=text(statusText,11.5f,Color.rgb(138,141,145),Typeface.NORMAL);st.setGravity(Gravity.END);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,dp(19));sp.gravity=Gravity.END;sp.rightMargin=dp(7);sp.topMargin=dp(1);outer.addView(st,sp);if("read".equals(m.optString("status")))main.postDelayed(()->{if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();},15000);}}return outer;}
        private String replyPreviewFromReply(JSONObject r){String b=r.optString("body").replaceFirst("^[🎤📷🎥🎬]\\s*","").trim();if(!b.isEmpty())return b;String t=r.optString("type");if("audio".equals(t))return"Voice message";if("image".equals(t))return"Photo";if("video".equals(t))return"Video";if("file".equals(t))return"File";if("shared_reel".equals(t))return"Reel";if("shared_post".equals(t))return"Post";return"Message";}
    }
    private void wireMessageGesture(
        View touchTarget,
        View messageTrack,
        JSONObject m,
        boolean mine,
        ImageView replyArrow,
        ReplyProgressView replyProgress
    ){
        final float[] downX={Float.NaN},downY={0f},offset={0f};
        final boolean[] replying={false},vertical={false},longOpened={false};
        final Runnable[] hold={null};

        touchTarget.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX[0]=e.getRawX();
                    downY[0]=e.getRawY();
                    offset[0]=0f;
                    replying[0]=false;
                    vertical[0]=false;
                    longOpened[0]=false;

                    messageTrack.animate().cancel();
                    replyArrow.animate().cancel();
                    replyProgress.animate().cancel();

                    replyArrow.setAlpha(0f);
                    replyArrow.setScaleX(.78f);
                    replyArrow.setScaleY(.78f);
                    replyArrow.setRotation(0f);
                    replyArrow.setTranslationX(0f);
                    replyArrow.setColorFilter(Color.rgb(139,142,148));
                    replyArrow.setBackgroundColor(Color.TRANSPARENT);

                    replyProgress.setProgress(0f);
                    replyProgress.setAlpha(0f);
                    replyProgress.setTranslationX(0f);

                    hold[0]=()->{
                        if(Float.isNaN(downX[0])||replying[0]||vertical[0])return;
                        longOpened[0]=true;
                        showMessageActions(m,messageTrack);
                    };
                    main.postDelayed(hold[0],165);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if(Float.isNaN(downX[0]))return false;

                    float dx=e.getRawX()-downX[0];
                    float dy=e.getRawY()-downY[0];
                    float ax=Math.abs(dx);
                    float ay=Math.abs(dy);
                    boolean correctDirection=mine?dx<0:dx>0;

                    if(!replying[0]){
                        // Capture the reply gesture very early. A later up/down
                        // movement must not cancel it.
                        if(correctDirection&&ax>=dp(5)&&ax>=ay*.55f){
                            replying[0]=true;
                            if(hold[0]!=null)main.removeCallbacks(hold[0]);
                            ViewParent parent=v.getParent();
                            if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);
                        }else if(ay>dp(15)&&ax<dp(5)){
                            vertical[0]=true;
                            if(hold[0]!=null)main.removeCallbacks(hold[0]);
                            downX[0]=Float.NaN;
                            ViewParent parent=v.getParent();
                            if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);
                            return false;
                        }
                    }

                    if(!replying[0])return true;

                    // Once captured, lock the reply direction. Crossing the
                    // original touch point in the opposite direction no longer
                    // cancels/restarts the reply gesture.
                    float raw=mine?Math.max(0f,-dx):Math.max(0f,dx);

                    // Instagram-like soft resistance.
                    float moved;
                    if(raw<=dp(30)){
                        moved=raw;
                    }else if(raw<=dp(58)){
                        moved=dp(30)+(raw-dp(30))*.58f;
                    }else{
                        moved=dp(46.25f)+(raw-dp(58))*.18f;
                    }
                    moved=Math.min(dp(58),moved);

                    offset[0]=mine?-moved:moved;
                    messageTrack.setTranslationX(offset[0]);

                    float triggerDistance=dp(46f);
                    float revealAt=dp(27f);
                    int replyGray=Color.rgb(139,142,148);

                    float visibleProgress=
                        moved<=revealAt
                            ?0f
                            :Math.min(1f,(moved-revealAt)/(triggerDistance-revealAt));

                    if(visibleProgress<=0f){
                        replyArrow.setAlpha(0f);
                        replyArrow.setBackgroundColor(Color.TRANSPARENT);
                        replyArrow.setColorFilter(replyGray);
                        replyProgress.setAlpha(0f);
                        replyProgress.setProgress(0f);
                    }else{
                        float iconTravel=dp(8f)*visibleProgress;
                        replyArrow.setTranslationX(mine?-iconTravel:iconTravel);
                        replyProgress.setTranslationX(mine?-iconTravel:iconTravel);

                        replyArrow.setAlpha(1f);
                        replyArrow.setScaleX(.93f+.07f*visibleProgress);
                        replyArrow.setScaleY(.93f+.07f*visibleProgress);

                        replyProgress.setProgress(visibleProgress);
                        replyProgress.setAlpha(1f);

                        if(visibleProgress>=.995f){
                            // Activated state: fully gray circle, white arrow.
                            replyArrow.setBackground(bg(replyGray,13.5f));
                            replyArrow.setColorFilter(Color.WHITE);
                        }else{
                            // Loading state: transparent circle, gray arrow/ring.
                            replyArrow.setBackgroundColor(Color.TRANSPARENT);
                            replyArrow.setColorFilter(replyGray);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(hold[0]!=null)main.removeCallbacks(hold[0]);

                    if(Float.isNaN(downX[0]))return false;

                    boolean trigger=
                        e.getActionMasked()==MotionEvent.ACTION_UP &&
                        replying[0] &&
                        Math.abs(offset[0])>=dp(46);
                    boolean tap=
                        e.getActionMasked()==MotionEvent.ACTION_UP &&
                        !replying[0] &&
                        !longOpened[0] &&
                        Math.abs(e.getRawX()-downX[0])<dp(8) &&
                        Math.abs(e.getRawY()-downY[0])<dp(8);

                    ViewParent parent=v.getParent();
                    if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);

                    if(trigger){
                        replyProgress.setProgress(1f);
                        replyProgress.setAlpha(1f);
                        replyArrow.setBackground(bg(Color.rgb(139,142,148),13.5f));
                        replyArrow.setColorFilter(Color.WHITE);
                    }

                    // Fast, subtle spring return.
                    messageTrack.animate()
                        .translationX(0f)
                        .setDuration(190)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(.42f))
                        .start();

                    replyArrow.animate()
                        .alpha(0f)
                        .scaleX(.78f)
                        .scaleY(.78f)
                        .translationX(0f)
                        .setDuration(150)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                    replyProgress.animate()
                        .alpha(0f)
                        .translationX(0f)
                        .setDuration(trigger?180:145)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                    downX[0]=Float.NaN;
                    replying[0]=false;

                    if(trigger&&!longOpened[0]){
                        setReply(m);

                        if(messageInput!=null){
                            messageInput.requestFocus();
                            messageInput.postDelayed(()->{
                                InputMethodManager imm=
                                    (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                                if(imm!=null){
                                    imm.showSoftInput(
                                        messageInput,
                                        InputMethodManager.SHOW_IMPLICIT
                                    );
                                }
                            },55);
                        }
                    }else if(tap){
                        messageTrack.performClick();
                    }
                    return true;
            }
            return false;
        });
    }
    private void setOlderLoaderVisible(boolean visible){
        if(olderMessagesSpinner==null)return;
        if(visible)olderMessagesSpinner.start();
        else olderMessagesSpinner.stop();
    }

    private void loadOlderMessagesPage(){
        if(
            loadingOlderMessages ||
            refreshingMessages ||
            activeConversation==null ||
            beforeCursor==null ||
            beforeCursor.isEmpty() ||
            list==null
        )return;

        final int headers=list.getHeaderViewsCount();
        final int firstVisible=list.getFirstVisiblePosition();

        // Find the first actually-visible MESSAGE row, not a header.
        int anchorPosition=-1;
        String anchorId="";
        int anchorTop=0;

        int childCount=list.getChildCount();
        for(int child=0;child<childCount;child++){
            int adapterPosition=firstVisible+child;
            int rowIndex=adapterPosition-headers;
            int messageIndex=messageAdapter==null?-1:messageAdapter.messageIndexForRow(rowIndex);

            if(messageIndex>=0&&messageIndex<messages.size()){
                String id=messages.get(messageIndex).optString("id","");
                if(!id.isEmpty()){
                    anchorPosition=adapterPosition;
                    anchorId=id;
                    View anchorView=list.getChildAt(child);
                    anchorTop=anchorView==null?0:anchorView.getTop();
                    break;
                }
            }
        }

        final String stableAnchorId=anchorId;
        final int stableAnchorTop=anchorTop;
        final int fallbackPosition=anchorPosition;

        loadingOlderMessages=true;

        // Show loader as list content, not on top of messages.
        setOlderLoaderVisible(true);

        final String conversationId=activeConversation.optString("id");
        final String cursor=beforeCursor;

        api.get(
            "/api/messaging/conversations/"+conversationId+
            "/messages?limit=80&before="+cursor,
            (json,error)->main.post(()->{
                if(
                    activeConversation==null ||
                    !conversationId.equals(activeConversation.optString("id"))
                ){
                    loadingOlderMessages=false;
                    setOlderLoaderVisible(false);
                    return;
                }

                if(error!=null){
                    loadingOlderMessages=false;
                    setOlderLoaderVisible(false);
                    return;
                }

                JSONArray arr=json.optJSONArray("messages");
                String next=json.optString("nextBefore","");
                beforeCursor=next;

                List<JSONObject> older=new ArrayList<>();
                Set<String> known=new HashSet<>();

                for(JSONObject existing:messages){
                    String id=existing.optString("id");
                    if(!id.isEmpty())known.add(id);
                }

                if(arr!=null){
                    for(int i=0;i<arr.length();i++){
                        JSONObject item=arr.optJSONObject(i);
                        if(item==null||item.optBoolean("deleted"))continue;

                        String id=item.optString("id");
                        if(!id.isEmpty()&&known.contains(id))continue;

                        older.add(item);
                        if(!id.isEmpty())known.add(id);
                    }
                }

                final int added=older.size();

                if(!older.isEmpty()){
                    messages.addAll(0,older);
                    cacheMessagesNow();

                    if(messageAdapter!=null){
                        messageAdapter.notifyDataSetChanged();
                    }
                }

                setOlderLoaderVisible(false);

                // Calculate the target adapter position once from the stable
                // anchor and apply it immediately before the next frame.
                int targetPosition=-1;

                if(!stableAnchorId.isEmpty()){
                    int newIndex=findMessageIndex(stableAnchorId);
                    if(newIndex>=0){
                        int newRow=messageAdapter==null?newIndex:messageAdapter.rowForMessageIndex(newIndex);
                        targetPosition=newRow+list.getHeaderViewsCount();
                    }
                }else if(fallbackPosition>=0){
                    targetPosition=fallbackPosition+added;
                }

                final int stableTargetPosition=targetPosition;

                if(stableTargetPosition>=0){
                    list.setSelectionFromTop(
                        stableTargetPosition,
                        stableAnchorTop
                    );

                    // One post-layout correction only, using the exact same
                    // message position and offset. No double-post bounce.
                    list.post(()->{
                        if(list!=null){
                            list.setSelectionFromTop(
                                stableTargetPosition,
                                stableAnchorTop
                            );
                        }
                        loadingOlderMessages=false;
                    });
                }else{
                    loadingOlderMessages=false;
                }
            })
        );
    }

    private void jumpToMessage(String messageId){if(messageId==null||messageId.isEmpty()||activeConversation==null)return;int index=findMessageIndex(messageId);if(index>=0){animateMessageTarget(index);return;}loadOlderUntil(messageId,0);}
    private int findMessageIndex(String id){for(int i=0;i<messages.size();i++)if(id.equals(messages.get(i).optString("id")))return i;return-1;}
    private void loadOlderUntil(String target,int attempts){if(attempts>=40||beforeCursor==null||beforeCursor.isEmpty()){toast("Message is no longer available.");return;}String cursor=beforeCursor;api.get("/api/messaging/conversations/"+activeConversation.optString("id")+"/messages?limit=80&before="+cursor,(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}JSONArray arr=json.optJSONArray("messages");beforeCursor=json.optString("nextBefore","");if(arr!=null){List<JSONObject> older=new ArrayList<>();for(int i=0;i<arr.length();i++){JSONObject x=arr.optJSONObject(i);if(x!=null)older.add(x);}messages.addAll(0,older);if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();}int idx=findMessageIndex(target);if(idx>=0)animateMessageTarget(idx);else if(!beforeCursor.isEmpty()&&!beforeCursor.equals(cursor))loadOlderUntil(target,attempts+1);else toast("Message is no longer available.");}));}
    private void animateMessageTarget(int messageIndex){
        if(list==null)return;

        int rowIndex=messageAdapter==null?messageIndex:messageAdapter.rowForMessageIndex(messageIndex);
        int position=rowIndex+list.getHeaderViewsCount();

        // ListView smoothScrollToPositionFromTop can overshoot a distant item
        // and then correct backward. Position it exactly once instead.
        list.post(()->{
            int desiredTop=Math.max(
                dp(70),
                list.getHeight()/2-dp(55)
            );

            list.setSelectionFromTop(position,desiredTop);

            list.postDelayed(()->{
                int first=list.getFirstVisiblePosition();
                int child=position-first;
                if(child<0||child>=list.getChildCount())return;

                View target=list.getChildAt(child);
                target.animate().cancel();
                target.setScaleX(1f);
                target.setScaleY(1f);

                target.animate()
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(50)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(()->main.postDelayed(
                        ()->target.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(70)
                            .setInterpolator(new DecelerateInterpolator())
                            .start(),
                        70
                    ))
                    .start();
            },45);
        });
    }

    private boolean isActuallyEdited(JSONObject m){String ea=m.optString("editedAt");if(ea.isEmpty())return false;Date e=parseDate(ea),c=parseDate(m.optString("createdAt"));return e!=null&&(c==null||e.getTime()-c.getTime()>1200);}

    private int mediaViewMode(JSONObject message,JSONObject attachment){int value=message==null?0:message.optInt("viewMode",message.optInt("mediaViewMode",message.optInt("viewLimit",0)));if(value==0&&attachment!=null)value=attachment.optInt("viewMode",attachment.optInt("mediaViewMode",attachment.optInt("viewLimit",0)));if(value==1||value==2)return value;String raw="";if(message!=null)raw=message.optString("viewMode",message.optString("mediaViewMode",message.optString("viewLimit",""))).toLowerCase(Locale.ROOT);if(raw.contains("once"))return 1;if(raw.contains("twice"))return 2;String name=attachment==null?"":(attachment.optString("name","")+" "+attachment.optString("url","")).toLowerCase(Locale.ROOT);if(name.contains("__vm1__")||name.contains("view-once"))return 1;if(name.contains("__vm2__")||name.contains("view-twice"))return 2;return 0;}
    private String mediaUrl(JSONObject attachment){String raw=attachment==null?"":attachment.optString("url","");return raw.startsWith("file:")||raw.startsWith("content:")||raw.startsWith("data:")?raw:api.absolute(raw);}
    private String displayMediaUrl(JSONObject attachment){if(attachment==null)return"";String local=attachment.optString("localPreviewUrl","");if(local.startsWith("file:")){String path=Uri.parse(local).getPath();if(path!=null&&new File(path).exists())return local;}return attachment.optString("url","");}
    private String actualMediaType(String declared,JSONObject attachment){if(attachment==null)return declared;String probe=(attachment.optString("mime",attachment.optString("mimeType",attachment.optString("type","")))+" "+attachment.optString("name","")+" "+attachment.optString("url","")).toLowerCase(Locale.ROOT);if(probe.contains("video/")||probe.matches(".*\\.(mp4|mov|m4v|webm|3gp)([? ].*)?$"))return"video";if(probe.contains("image/")||probe.matches(".*\\.(jpg|jpeg|png|webp|gif|heic)([? ].*)?$"))return"image";return declared;}
    private int stableMediaHeight(JSONObject attachment,int width,boolean stickerLike){if(stickerLike)return width;int sourceW=attachment==null?0:attachment.optInt("width",attachment.optInt("naturalWidth",0)),sourceH=attachment==null?0:attachment.optInt("height",attachment.optInt("naturalHeight",0));if(sourceW<=0||sourceH<=0)return dp(135);return Math.max(dp(96),Math.min(dp(210),Math.round(width*(sourceH/(float)sourceW))));}
    private String ephemeralKey(JSONObject message,JSONObject attachment){String id=message==null?"":message.optString("id",message.optString("clientId",""));if(id.isEmpty()&&attachment!=null)id=attachment.optString("url",attachment.optString("name","media"));return "ephemeral-views-"+id;}
    private int remainingMediaViews(JSONObject message,JSONObject attachment){int mode=mediaViewMode(message,attachment);if(mode<=0||isMine(message))return mode;return getSharedPreferences("messenger_ephemeral_media",MODE_PRIVATE).getInt(ephemeralKey(message,attachment),mode);}
    private void consumeMediaView(JSONObject message,JSONObject attachment){if(isMine(message))return;int left=remainingMediaViews(message,attachment);if(left<=0)return;getSharedPreferences("messenger_ephemeral_media",MODE_PRIVATE).edit().putInt(ephemeralKey(message,attachment),left-1).apply();if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();}
    private String ephemeralRelativeTime(String value){Date dt=parseDate(value);if(dt==null)return"now";long sec=Math.max(0,(System.currentTimeMillis()-dt.getTime())/1000);if(sec<60)return"now";long min=sec/60;if(min<60)return min+"m";long hour=min/60;if(hour<24)return hour+"h";return(hour/24)+"d";}
    private View buildEphemeralMediaPill(JSONObject message,JSONObject attachment,boolean mine,String type,int mode){LinearLayout pill=new LinearLayout(this);pill.setGravity(Gravity.CENTER_VERTICAL);pill.setPadding(dp(12),0,dp(14),0);int fill=mine?themeSentColors()[0]:themeReceived();int color=mine?themeSentText():themeReceivedText();pill.setBackground(bg(fill,22));ImageView iconView=new ImageView(this);iconView.setImageResource(mode==1?R.drawable.ic_camera_view_once_ref:R.drawable.ic_camera_view_twice_ref);iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);iconView.setColorFilter(color);pill.addView(iconView,new LinearLayout.LayoutParams(dp(20),dp(20)));boolean expired=!mine&&remainingMediaViews(message,attachment)<=0;String noun="video".equals(type)?"video":"photo",capital=Character.toUpperCase(noun.charAt(0))+noun.substring(1);String label=mine||expired?capital:"View "+noun;TextView title=text(label,15.5f,color,Typeface.BOLD);LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,dp(44));tlp.leftMargin=dp(6);pill.addView(title,tlp);pill.setMinimumHeight(dp(44));pill.setAlpha(1f);if(mine||expired){pill.setEnabled(false);return pill;}pill.setOnClickListener(v->{if("video".equals(type))showVideoMediaViewer(message,attachment);else showMediaViewer(message,attachment);});Runnable ready=()->pill.post(()->{pill.setEnabled(true);pill.setAlpha(1f);});boolean preloaded;if("video".equals(type)){preloaded=!temporaryVideoLocalUrl(attachment.optString("url")).isEmpty();if(!preloaded)preloadTemporaryVideo(attachment,ready);}else{preloaded=images.isReady(attachment.optString("url"));if(!preloaded)images.prefetch(attachment.optString("url"),ready);}pill.setEnabled(preloaded);pill.setAlpha(preloaded?1f:.68f);return pill;}
    private View buildMessageContent(JSONObject m,boolean mine,boolean samePrev,boolean sameNext){
        String type=m.optString("type","text");
        JSONArray att=m.optJSONArray("attachments");
        JSONObject a=att!=null&&att.length()>0?att.optJSONObject(0):null;
        if(a!=null)type=actualMediaType(type,a);
        if("audio".equals(type)&&a!=null){int voiceBg=mine?themeSentColors()[0]:themeReceived();int voiceText=mine?themeSentText():themeReceivedText();int voiceAccent=contrastAccent(voiceBg,themeAccent(),voiceText);VoiceMessageView v=new VoiceMessageView(this,mediaUrl(a),a.optString("name"),api.mediaHeaders(),voiceAccent,voiceBg,voiceText,voiceText);v.setLayoutParams(new LinearLayout.LayoutParams(Math.min(dp(310),(int)(getResources().getDisplayMetrics().widthPixels*.72f)),dp(78)));return v;}
        if(("image".equals(type)||"video".equals(type))&&a!=null){int mode=mediaViewMode(m,a);if(mode>0)return buildEphemeralMediaPill(m,a,mine,type,mode);}
        if("image".equals(type)&&a!=null){
            String url=displayMediaUrl(a),mime=a.optString("mime",a.optString("mimeType",a.optString("type"))),fileName=a.optString("name","");
            boolean stickerLike=m.optBoolean("sticker")||a.optBoolean("sticker")||mime.toLowerCase(Locale.ROOT).contains("gif")||fileName.toLowerCase(Locale.ROOT).endsWith(".gif")||fileName.toLowerCase(Locale.ROOT).startsWith("sticker-")||url.contains("giphy.com");
            int width=stickerLike?dp(108):Math.min(dp(180),(int)(getResources().getDisplayMetrics().widthPixels*.52f)),height=stableMediaHeight(a,width,stickerLike);
            ImageView im=new ImageView(this);im.setAdjustViewBounds(false);im.setScaleType(stickerLike?ImageView.ScaleType.CENTER_INSIDE:ImageView.ScaleType.CENTER_CROP);im.setBackgroundColor(Color.TRANSPARENT);im.setClipToOutline(false);
            if(stickerLike){
                FrameLayout stickerHost=new FrameLayout(this);stickerHost.setLayoutParams(new LinearLayout.LayoutParams(width,height));
                stickerHost.addView(im,new FrameLayout.LayoutParams(width,height,Gravity.CENTER));
                ProgressBar loading=new ProgressBar(this);loading.setIndeterminate(true);loading.setAlpha(.88f);stickerHost.addView(loading,new FrameLayout.LayoutParams(dp(25),dp(25),Gravity.CENTER));
                im.setAlpha(0f);stickers.load(url,im,()->{if(!url.equals(im.getTag()))return;im.animate().alpha(1f).setDuration(110).start();loading.animate().alpha(0f).setDuration(100).withEndAction(()->loading.setVisibility(View.GONE)).start();});
                return stickerHost;
            }
            im.setLayoutParams(new LinearLayout.LayoutParams(width,height));im.setClipToOutline(true);im.setBackground(bg(Color.rgb(17,17,17),15));images.load(url,im);im.setOnClickListener(v->showMediaViewer(m,a,im));return im;
        }
        if("video".equals(type)&&a!=null){VideoView vv=new VideoView(this);String preview=displayMediaUrl(a);String playable=preview.startsWith("file:")?preview:mediaUrl(a);vv.setVideoURI(Uri.parse(playable),api.mediaHeaders());vv.setOnPreparedListener(mp->{mp.setVolume(0f,0f);vv.seekTo(1);});vv.setOnClickListener(v->showVideoMediaViewer(m,a));int w=Math.min(dp(180),(int)(getResources().getDisplayMetrics().widthPixels*.52f));vv.setLayoutParams(new LinearLayout.LayoutParams(w,dp(132)));return vv;}
        if("file".equals(type)&&a!=null){LinearLayout file=new LinearLayout(this);file.setGravity(Gravity.CENTER_VERTICAL);file.setPadding(dp(10),dp(8),dp(10),dp(8));TextView clip=text("📎",20,TEXT,Typeface.NORMAL);file.addView(clip,new LinearLayout.LayoutParams(dp(34),dp(42)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);file.addView(copy,new LinearLayout.LayoutParams(dp(190),dp(48)));copy.addView(text(a.optString("name","File"),13,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(27)));copy.addView(text(fileSize(a.optLong("size")),10,SUB,Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(18)));return file;}
        if("shared_reel".equals(type)||"shared_post".equals(type)){TextView card=text("shared_reel".equals(type)?"Reel":"Post",15,TEXT,Typeface.BOLD);card.setGravity(Gravity.CENTER);card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(bg(RECEIVED,15));return card;}
        String body=m.optString("body");TextView bubble=text(body,15,mine?themeSentText():themeReceivedText(),Typeface.NORMAL);bubble.setPadding(dp(12),dp(9),dp(12),dp(9));bubble.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*.78f));bubble.setBackground(bubbleBg(mine,samePrev,sameNext));return bubble;
    }
    private int contrastAccent(int bg,int preferred,int text){int br=Color.red(bg),bgc=Color.green(bg),bb=Color.blue(bg),pr=Color.red(preferred),pg=Color.green(preferred),pb=Color.blue(preferred);double lumBg=.299*br+.587*bgc+.114*bb,lumPref=.299*pr+.587*pg+.114*pb;return Math.abs(lumBg-lumPref)<68?text:preferred;}
    private String relativeTime(String value){Date dt=parseDate(value);if(dt==null)return"";long sec=Math.max(0,(System.currentTimeMillis()-dt.getTime())/1000);if(sec<60)return"Just now";long m=sec/60;if(m<60)return m+"m";long h=m/60;if(h<24)return h+"h";return(h/24)+"d";}
    private void showTimedVideoViewer(JSONObject message,JSONObject attachment){
        if(isMine(message))return;
        if(remainingMediaViews(message,attachment)<=0){toast("This video is no longer available.");return;}
        String preparedUrl=temporaryVideoLocalUrl(attachment.optString("url"));
        if(preparedUrl.isEmpty()){preloadTemporaryVideo(attachment,null);toast("Video is still loading.");return;}
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.BLACK);
        VideoView video=new VideoView(this);video.setClickable(true);video.setAlpha(0f);video.setVideoURI(Uri.parse(preparedUrl));
        FrameLayout.LayoutParams videoLp=new FrameLayout.LayoutParams(-1,-1);videoLp.topMargin=dp(54);overlay.addView(video,videoLp);
        ProgressBar loading=new ProgressBar(this);FrameLayout.LayoutParams loadingLp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER);overlay.addView(loading,loadingLp);
        TimedProgressView progress=new TimedProgressView(this);FrameLayout.LayoutParams progressLp=new FrameLayout.LayoutParams(-1,dp(4),Gravity.TOP);progressLp.leftMargin=dp(9);progressLp.rightMargin=dp(9);progressLp.topMargin=dp(13);overlay.addView(progress,progressLp);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);FrameLayout.LayoutParams headerLp=new FrameLayout.LayoutParams(-1,dp(38),Gravity.TOP);headerLp.leftMargin=dp(12);headerLp.rightMargin=dp(12);headerLp.topMargin=dp(20);overlay.addView(header,headerLp);
        JSONObject sender=message.optJSONObject("sender");String name=senderName(message);View avatar=buildUserAvatar(avatarUrl(sender),name,30);header.addView(avatar,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout words=new LinearLayout(this);words.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams wordsLp=new LinearLayout.LayoutParams(0,dp(36),1);wordsLp.leftMargin=dp(7);header.addView(words,wordsLp);words.addView(text(name,14.5f,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-2,-1));words.addView(text(" to you",14.5f,Color.WHITE,Typeface.NORMAL),new LinearLayout.LayoutParams(-2,-1));TextView time=text(" "+ephemeralRelativeTime(message.optString("createdAt")),14.5f,Color.rgb(155,158,164),Typeface.NORMAL);words.addView(time,new LinearLayout.LayoutParams(-2,-1));
        final boolean[] resumeAfterHold={false},started={false};final Runnable[] ticker={null};
        video.setOnPreparedListener(mp->{if(!d.isShowing())return;if(!started[0]){started[0]=true;consumeMediaView(message,attachment);}loading.setVisibility(View.GONE);video.setAlpha(1f);mp.setLooping(false);video.start();ticker[0]=new Runnable(){public void run(){int duration=Math.max(1,video.getDuration()),position=Math.max(0,video.getCurrentPosition());progress.setProgress(1f-Math.min(1f,position/(float)duration));time.setText(" "+ephemeralRelativeTime(message.optString("createdAt")));if(d.isShowing())main.postDelayed(this,33);}};main.post(ticker[0]);});
        video.setOnErrorListener((mp,what,extra)->{loading.setVisibility(View.GONE);toast("Couldn't load this video.");d.dismiss();return true;});
        video.setOnCompletionListener(mp->d.dismiss());
        wireTemporaryMediaHold(video,()->{resumeAfterHold[0]=video.isPlaying();if(resumeAfterHold[0])video.pause();},()->{if(resumeAfterHold[0]&&d.isShowing())video.start();resumeAfterHold[0]=false;});
        d.setOnDismissListener(x->{if(ticker[0]!=null)main.removeCallbacks(ticker[0]);try{video.stopPlayback();}catch(Exception ignored){}if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();});
        d.setContentView(overlay);d.show();Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
    }

    private static final class SnapMediaScroll extends HorizontalScrollView{
        interface PageChanged{void changed(int index);}
        private PageChanged listener;private int page=0;private float downX;
        SnapMediaScroll(Context context){super(context);setSmoothScrollingEnabled(true);}
        void setOnPageChanged(PageChanged value){listener=value;}
        int currentPage(){return page;}
        void jumpToPage(int value){page=Math.max(0,value);scrollTo(page*Math.max(1,getWidth()),0);if(listener!=null)listener.changed(page);}
        @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();animate().cancel();}boolean handled=super.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){int width=Math.max(1,getWidth()),count=getChildCount()==0?1:Math.max(1,((ViewGroup)getChildAt(0)).getChildCount());float drag=downX-event.getX();int target=Math.round(getScrollX()/(float)width);if(Math.abs(drag)>width*.18f)target=drag>0?Math.max(target,page+1):Math.min(target,page-1);target=Math.max(0,Math.min(count-1,target));page=target;smoothScrollTo(page*width,0);if(listener!=null)listener.changed(page);}return handled;}
    }

    private static final class StackMediaItem{
        final JSONObject message,attachment;final String type;
        StackMediaItem(JSONObject m,JSONObject a,String t){message=m;attachment=a;type=t;}
    }

    private void showMediaStackGallery(int start,int end){
        final ArrayList<StackMediaItem> items=new ArrayList<>();
        for(int i=start;i<=end;i++){JSONObject m=messages.get(i),a=mediaStackAttachment(m);if(a!=null)items.add(new StackMediaItem(m,a,actualMediaType(m.optString("type"),a)));}
        if(items.isEmpty())return;
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final FrameLayout rootView=new FrameLayout(this);rootView.setBackgroundColor(Color.rgb(9,13,17));
        final FrameLayout galleryLayer=new FrameLayout(this);rootView.addView(galleryLayer,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(dp(28),dp(82),dp(28),dp(28));galleryLayer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        GridLayout grid=new GridLayout(this);grid.setColumnCount(2);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        final ArrayList<View> cells=new ArrayList<>();int screen=getResources().getDisplayMetrics().widthPixels,cellW=(screen-dp(68))/2,cellH=Math.round(cellW*1.27f);
        for(int i=0;i<items.size();i++){
            StackMediaItem item=items.get(i);FrameLayout cell=new FrameLayout(this);cell.setClipToOutline(true);cell.setBackground(bg(Color.rgb(22,25,29),15));
            GridLayout.LayoutParams cp=new GridLayout.LayoutParams();cp.width=cellW;cp.height=cellH;cp.rowSpec=GridLayout.spec(i/2);cp.columnSpec=GridLayout.spec(i%2);cp.setMargins(dp(3),dp(8),dp(3),dp(8));grid.addView(cell,cp);
            ImageView media=new ImageView(this);media.setScaleType(ImageView.ScaleType.CENTER_CROP);media.setBackgroundColor(Color.rgb(20,22,26));cell.addView(media,new FrameLayout.LayoutParams(-1,-1));
            if("video".equals(item.type)){String poster=item.attachment.optString("thumbnailUrl",item.attachment.optString("previewUrl",item.attachment.optString("posterUrl",displayMediaUrl(item.attachment))));if(!poster.isEmpty())images.load(poster,media);TextView play=text("▶",26,Color.WHITE,Typeface.BOLD);play.setGravity(Gravity.CENTER);play.setShadowLayer(dp(2),0,dp(1),Color.BLACK);cell.addView(play,new FrameLayout.LayoutParams(-1,-1));}else images.load(displayMediaUrl(item.attachment),media);
            final int selected=i;cell.setOnClickListener(v->openStackMediaPager(rootView,galleryLayer,items,cells,selected,d));cells.add(cell);
        }
        ImageButton close=icon(R.drawable.ic_msg_close,48,Color.WHITE);FrameLayout.LayoutParams closeLp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.START);closeLp.leftMargin=dp(13);closeLp.topMargin=dp(19);galleryLayer.addView(close,closeLp);close.setOnClickListener(v->d.dismiss());
        d.setContentView(rootView);d.show();Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
    }

    private android.graphics.drawable.Drawable galleryCellDrawable(View cell){if(cell instanceof ViewGroup){ViewGroup group=(ViewGroup)cell;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child instanceof ImageView){android.graphics.drawable.Drawable drawable=((ImageView)child).getDrawable();if(drawable!=null)return drawable;}}}return null;}
    private void stopStackVideos(List<VideoView> videos,int active){for(int i=0;i<videos.size();i++){VideoView v=videos.get(i);if(v==null)continue;if(i==active){if(!v.isPlaying())v.start();}else if(v.isPlaying())v.pause();}}
    private void loadStackPagerWindow(ArrayList<StackMediaItem> items,ArrayList<ImageView> pageImages,int index){for(int i=Math.max(0,index-1);i<=Math.min(items.size()-1,index+1);i++){ImageView image=pageImages.get(i);if(image!=null&&image.getTag()==null)images.load(displayMediaUrl(items.get(i).attachment),image);}}

    private void openStackMediaPager(FrameLayout rootView,FrameLayout galleryLayer,ArrayList<StackMediaItem> items,ArrayList<View> cells,int selected,Dialog dialog){
        if(rootView.findViewWithTag("stack-media-viewer")!=null)return;
        final FrameLayout viewer=new FrameLayout(this);viewer.setTag("stack-media-viewer");rootView.addView(viewer,new FrameLayout.LayoutParams(-1,-1));
        View darkness=new View(this);darkness.setBackgroundColor(Color.rgb(9,12,16));darkness.setAlpha(0f);viewer.addView(darkness,new FrameLayout.LayoutParams(-1,-1));
        final SnapMediaScroll pager=new SnapMediaScroll(this);pager.setFillViewport(true);pager.setHorizontalScrollBarEnabled(false);pager.setOverScrollMode(View.OVER_SCROLL_NEVER);pager.setClipToPadding(true);
        FrameLayout.LayoutParams pagerLp=new FrameLayout.LayoutParams(-1,-1);pagerLp.topMargin=dp(66);pagerLp.bottomMargin=dp(72);viewer.addView(pager,pagerLp);
        LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);pager.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));
        int pageW=getResources().getDisplayMetrics().widthPixels;final ArrayList<VideoView> videos=new ArrayList<>();final ArrayList<ImageView> pageImages=new ArrayList<>();
        for(int i=0;i<items.size();i++){
            StackMediaItem item=items.get(i);FrameLayout page=new FrameLayout(this);page.setPadding(dp(12),0,dp(12),0);strip.addView(page,new LinearLayout.LayoutParams(pageW,-1));
            if("video".equals(item.type)){
                VideoView video=new VideoView(this);video.setBackgroundColor(Color.BLACK);String playable=displayMediaUrl(item.attachment);if(!playable.startsWith("file:"))playable=mediaUrl(item.attachment);video.setVideoURI(Uri.parse(playable),api.mediaHeaders());video.setOnPreparedListener(mp->{mp.setLooping(false);if(pager.currentPage()==strip.indexOfChild(page))video.start();});page.addView(video,new FrameLayout.LayoutParams(-1,-1));videos.add(video);pageImages.add(null);
            }else{
                ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.BLACK);android.graphics.drawable.Drawable preview=galleryCellDrawable(cells.get(i));if(preview!=null)image.setImageDrawable(preview);page.addView(image,new FrameLayout.LayoutParams(-1,-1));videos.add(null);pageImages.add(image);
            }
        }
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(7),dp(8),dp(7));viewer.addView(top,new FrameLayout.LayoutParams(-1,dp(66),Gravity.TOP));ImageButton close=icon(R.drawable.ic_msg_close,46,Color.WHITE);top.addView(close);
        StackMediaItem initial=items.get(selected);JSONObject sender=initial.message.optJSONObject("sender");String name=isMine(initial.message)?"You":senderName(initial.message);View avatar=buildUserAvatar(avatarUrl(sender),name,34);LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(dp(34),dp(34));avp.leftMargin=dp(3);top.addView(avatar,avp);LinearLayout who=new LinearLayout(this);who.setOrientation(LinearLayout.VERTICAL);who.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams whoLp=new LinearLayout.LayoutParams(0,dp(46),1);whoLp.leftMargin=dp(7);top.addView(who,whoLp);who.addView(text(name,14.5f,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(23)));who.addView(text(relativeTime(initial.message.optString("createdAt")),12.5f,Color.rgb(190,194,200),Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(20)));
        LinearLayout reply=new LinearLayout(this);reply.setGravity(Gravity.CENTER_VERTICAL);reply.setPadding(dp(14),0,dp(14),0);reply.setBackground(bg(Color.rgb(54,57,62),27));FrameLayout.LayoutParams replyLp=new FrameLayout.LayoutParams(-1,dp(54),Gravity.BOTTOM);replyLp.setMargins(dp(12),0,dp(12),dp(11));viewer.addView(reply,replyLp);TextView replyHint=text("Reply…",16.5f,Color.rgb(185,188,194),Typeface.NORMAL);reply.addView(replyHint,new LinearLayout.LayoutParams(0,-1,1));TextView heart=text("♡",28,Color.WHITE,Typeface.NORMAL);heart.setGravity(Gravity.CENTER);reply.addView(heart,new LinearLayout.LayoutParams(dp(34),dp(34)));
        final boolean[] closing={false};final Runnable closeViewer=()->{if(closing[0])return;closing[0]=true;int index=Math.max(0,Math.min(items.size()-1,pager.currentPage()));View target=cells.get(index);int[] targetAt=new int[2],pagerAt=new int[2];target.getLocationOnScreen(targetAt);pager.getLocationOnScreen(pagerAt);float sx=target.getWidth()/(float)Math.max(1,pager.getWidth()),sy=target.getHeight()/(float)Math.max(1,pager.getHeight()),tx=targetAt[0]-pagerAt[0],ty=targetAt[1]-pagerAt[1];galleryLayer.animate().cancel();galleryLayer.setAlpha(0f);galleryLayer.setVisibility(View.VISIBLE);galleryLayer.animate().alpha(1f).setDuration(190).start();top.animate().alpha(0f).setDuration(110).start();reply.animate().alpha(0f).setDuration(110).start();darkness.animate().alpha(0f).setDuration(180).start();pager.setPivotX(0);pager.setPivotY(0);pager.animate().scaleX(sx).scaleY(sy).translationX(tx).translationY(ty).setDuration(205).setInterpolator(new DecelerateInterpolator(1.7f)).withEndAction(()->{for(VideoView video:videos)if(video!=null)try{video.stopPlayback();}catch(Exception ignored){}rootView.removeView(viewer);}).start();};
        close.setOnClickListener(v->closeViewer.run());pager.setOnPageChanged(index->{loadStackPagerWindow(items,pageImages,index);stopStackVideos(videos,index);});viewer.setOnKeyListener((v,key,event)->{if(key==android.view.KeyEvent.KEYCODE_BACK&&event.getAction()==android.view.KeyEvent.ACTION_UP){closeViewer.run();return true;}return false;});viewer.setFocusableInTouchMode(true);viewer.requestFocus();
        pager.post(()->{pager.jumpToPage(selected);loadStackPagerWindow(items,pageImages,selected);stopStackVideos(videos,selected);int[] targetAt=new int[2],pagerAt=new int[2];cells.get(selected).getLocationOnScreen(targetAt);pager.getLocationOnScreen(pagerAt);float sx=cells.get(selected).getWidth()/(float)Math.max(1,pager.getWidth()),sy=cells.get(selected).getHeight()/(float)Math.max(1,pager.getHeight()),tx=targetAt[0]-pagerAt[0],ty=targetAt[1]-pagerAt[1];pager.setPivotX(0);pager.setPivotY(0);pager.setScaleX(sx);pager.setScaleY(sy);pager.setTranslationX(tx);pager.setTranslationY(ty);galleryLayer.animate().alpha(0f).setDuration(190).withEndAction(()->galleryLayer.setVisibility(View.INVISIBLE)).start();darkness.animate().alpha(1f).setDuration(190).start();pager.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(215).setInterpolator(new DecelerateInterpolator(1.7f)).start();});
        dialog.setOnKeyListener((x,key,event)->{if(key==android.view.KeyEvent.KEYCODE_BACK&&event.getAction()==android.view.KeyEvent.ACTION_UP&&rootView.findViewWithTag("stack-media-viewer")!=null){closeViewer.run();return true;}return false;});
    }

    private void showVideoMediaViewer(JSONObject message,JSONObject attachment){
        if(mediaViewMode(message,attachment)>0){showTimedVideoViewer(message,attachment);return;}
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.BLACK);
        VideoView video=new VideoView(this);video.setVideoURI(Uri.parse(mediaUrl(attachment)),api.mediaHeaders());FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.setMargins(dp(12),dp(72),dp(12),dp(72));overlay.addView(video,vp);
        android.widget.MediaController controls=new android.widget.MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setOnPreparedListener(mp->{mp.setLooping(false);video.start();});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(8),dp(8),dp(8));overlay.addView(top,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));ImageButton close=icon(R.drawable.ic_msg_close,42,Color.WHITE);top.addView(close);close.setOnClickListener(v->d.dismiss());JSONObject sender=message.optJSONObject("sender");String name=isMine(message)?"You":senderName(message);View avatar=buildUserAvatar(avatarUrl(sender),name,36);LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(dp(36),dp(36));avatarLp.leftMargin=dp(4);top.addView(avatar,avatarLp);LinearLayout who=new LinearLayout(this);who.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(0,dp(48),1);wlp.leftMargin=dp(8);top.addView(who,wlp);who.addView(text(name,15,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-2,-1));who.addView(text(" "+ephemeralRelativeTime(message.optString("createdAt")),15,Color.rgb(155,158,164),Typeface.NORMAL),new LinearLayout.LayoutParams(-2,-1));
        d.setOnDismissListener(x->{try{video.stopPlayback();}catch(Exception ignored){}});d.setContentView(overlay);d.show();Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
    }

    private void showTimedPhotoViewer(JSONObject message,JSONObject attachment){
        if(isMine(message))return;
        if(remainingMediaViews(message,attachment)<=0){toast("This photo is no longer available.");return;}
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.BLACK);
        boolean alreadyPreloaded=images.isReady(attachment.optString("url"));ZoomImageView image=new ZoomImageView(this);image.setAlpha(alreadyPreloaded?1f:0f);overlay.addView(image,new FrameLayout.LayoutParams(-1,-1));
        ProgressBar loading=new ProgressBar(this);loading.setVisibility(alreadyPreloaded?View.GONE:View.VISIBLE);overlay.addView(loading,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
        TimedProgressView progress=new TimedProgressView(this);FrameLayout.LayoutParams progressLp=new FrameLayout.LayoutParams(-1,dp(4),Gravity.TOP);progressLp.leftMargin=dp(9);progressLp.rightMargin=dp(9);progressLp.topMargin=dp(13);overlay.addView(progress,progressLp);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);FrameLayout.LayoutParams headerLp=new FrameLayout.LayoutParams(-1,dp(38),Gravity.TOP);headerLp.leftMargin=dp(12);headerLp.rightMargin=dp(12);headerLp.topMargin=dp(20);overlay.addView(header,headerLp);
        JSONObject sender=message.optJSONObject("sender");String name=senderName(message);View avatar=buildUserAvatar(avatarUrl(sender),name,30);header.addView(avatar,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout words=new LinearLayout(this);words.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams wordsLp=new LinearLayout.LayoutParams(0,dp(36),1);wordsLp.leftMargin=dp(7);header.addView(words,wordsLp);words.addView(text(name,14.5f,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-2,-1));words.addView(text(" to you",14.5f,Color.WHITE,Typeface.NORMAL),new LinearLayout.LayoutParams(-2,-1));TextView timeView=text(" "+ephemeralRelativeTime(message.optString("createdAt")),14.5f,Color.rgb(155,158,164),Typeface.NORMAL);words.addView(timeView,new LinearLayout.LayoutParams(-2,-1));
        final PausableClock[] clock={null};final boolean[] started={false};final Runnable[] ticker={null};ticker[0]=new Runnable(){public void run(){if(clock[0]==null)return;float elapsed=clock[0].fraction(15000L);progress.setProgress(1f-Math.min(1f,elapsed));timeView.setText(" "+ephemeralRelativeTime(message.optString("createdAt")));if(elapsed>=1f){if(d.isShowing())d.dismiss();return;}main.postDelayed(this,16);}};
        wireTemporaryMediaHold(image,()->{if(clock[0]!=null)clock[0].pause();},()->{if(clock[0]!=null)clock[0].resume();});
        image.setOnDismissListener(d::dismiss);
        d.setOnDismissListener(x->{main.removeCallbacks(ticker[0]);if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();});
        d.setContentView(overlay);images.load(attachment.optString("url"),image,()->{if(!d.isShowing()||started[0])return;started[0]=true;consumeMediaView(message,attachment);clock[0]=new PausableClock();loading.setVisibility(View.GONE);image.setAlpha(1f);main.post(ticker[0]);});d.show();Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
    }

    private void showMediaViewer(JSONObject message,JSONObject attachment){showMediaViewer(message,attachment,null);}
    private void showMediaViewer(JSONObject message,JSONObject attachment,ImageView source){if(mediaViewMode(message,attachment)>0){showTimedPhotoViewer(message,attachment);return;}final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.rgb(10,12,16));ImageView backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setAlpha(.82f);backdrop.setScaleX(1.16f);backdrop.setScaleY(1.16f);if(Build.VERSION.SDK_INT>=31)backdrop.setRenderEffect(RenderEffect.createBlurEffect(dp(35),dp(35),Shader.TileMode.CLAMP));overlay.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));android.graphics.drawable.Drawable immediate=source==null?null:source.getDrawable();if(immediate!=null)backdrop.setImageDrawable(immediate);images.load(attachment.optString("url"),backdrop);View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xaa05070a,0x33080a0e,0x5505070a}));overlay.addView(shade,new FrameLayout.LayoutParams(-1,-1));ZoomImageView image=new ZoomImageView(this);if(immediate!=null)image.setImageDrawable(immediate);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-1);overlay.addView(image,ip);images.load(attachment.optString("url"),image);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(8),dp(8),dp(8));overlay.addView(top,new FrameLayout.LayoutParams(-1,dp(74),Gravity.TOP));ImageButton close=icon(R.drawable.ic_msg_close,42,Color.WHITE);top.addView(close);close.setOnClickListener(v->d.dismiss());JSONObject sender=message.optJSONObject("sender");String name=isMine(message)?"You":senderName(message);View av=buildUserAvatar(avatarUrl(sender),name,38);LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(dp(38),dp(38));avp.leftMargin=dp(4);top.addView(av,avp);LinearLayout author=new LinearLayout(this);author.setOrientation(LinearLayout.VERTICAL);author.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(0,dp(48),1);alp.leftMargin=dp(7);top.addView(author,alp);author.addView(text(name,15,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(24)));author.addView(text(relativeTime(message.optString("createdAt")),13,Color.rgb(199,203,209),Typeface.NORMAL),new LinearLayout.LayoutParams(-1,dp(22)));ImageButton story=icon(R.drawable.ic_media_story,42,Color.WHITE);top.addView(story);story.setOnClickListener(v->{story.setEnabled(false);new Thread(()->{try{byte[] raw=api.getBytesSync(attachment.optString("url"));String mime=attachment.optString("mime","image/jpeg");String data="data:"+mime+";base64,"+Base64.encodeToString(raw,Base64.NO_WRAP);api.post("/api/stories",new JSONObject().put("image",data).put("caption",""),(json,error)->main.post(()->{story.setEnabled(true);if(error!=null)toast(error.getMessage());else{toast("Added to story");d.dismiss();}}));}catch(Exception ex){main.post(()->{story.setEnabled(true);toast(ex.getMessage());});}}).start();});ImageButton more=icon(R.drawable.ic_media_more,42,Color.WHITE);more.setPadding(dp(4),dp(4),dp(4),dp(4));top.addView(more);LinearLayout menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(dp(5),dp(5),dp(5),dp(5));menu.setBackground(bg(Color.rgb(41,45,51),15));menu.setVisibility(View.GONE);FrameLayout.LayoutParams mlp=new FrameLayout.LayoutParams(dp(172),dp(58),Gravity.TOP|Gravity.END);mlp.setMargins(0,dp(63),dp(12),0);overlay.addView(menu,mlp);LinearLayout download=actionRow("Download",R.drawable.ic_media_download,false);menu.addView(download,new LinearLayout.LayoutParams(-1,dp(48)));download.setOnClickListener(v->{menu.setVisibility(View.GONE);try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(api.absolute(attachment.optString("url")))));}catch(Exception e){toast(e.getMessage());}});more.setOnClickListener(v->menu.setVisibility(menu.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));LinearLayout reply=new LinearLayout(this);reply.setGravity(Gravity.CENTER_VERTICAL);reply.setPadding(dp(7),dp(5),dp(7),dp(5));reply.setBackground(bg(Color.rgb(59,61,65),29));FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(-1,dp(54),Gravity.BOTTOM);rp.setMargins(dp(12),0,dp(12),dp(12));overlay.addView(reply,rp);ImageButton camera=icon(R.drawable.ic_media_camera,42,Color.rgb(7,9,12));camera.setBackground(bg(Color.WHITE,21));reply.addView(camera);camera.setOnClickListener(v->{replyTo=message;pickAttachment();});EditText input=new EditText(this);input.setHint("Reply…");input.setHintTextColor(Color.rgb(182,184,189));input.setTextColor(Color.WHITE);input.setTextSize(17);input.setSingleLine(true);input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(10),0,dp(8),0);reply.addView(input,new LinearLayout.LayoutParams(0,dp(42),1));ImageButton send=icon(R.drawable.msg_send_enabled,42,themeAccent());Bitmap mediaSend=themedIconBitmap(R.drawable.msg_send_enabled,themeAccent());if(mediaSend!=null)send.setImageBitmap(mediaSend);send.clearColorFilter();send.setVisibility(View.GONE);reply.addView(send);input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){send.setVisibility(s.toString().trim().isEmpty()?View.GONE:View.VISIBLE);}public void afterTextChanged(Editable e){}});send.setOnClickListener(v->{String body=input.getText().toString().trim();if(body.isEmpty())return;try{JSONObject req=new JSONObject().put("body",body).put("clientId","photo-reply-"+System.currentTimeMillis()).put("replyToId",message.optString("id"));api.post("/api/messaging/conversations/"+activeConversation.optString("id")+"/messages",req,(json,error)->main.post(()->{if(error!=null){toast(error.getMessage());return;}input.setText("");JSONObject nm=json.optJSONObject("message");if(nm!=null)upsertMessage(nm);}));}catch(Exception ex){toast(ex.getMessage());}});final boolean[] chrome={true};image.setOnSingleTapListener(()->{chrome[0]=!chrome[0];if(!chrome[0])menu.setVisibility(View.GONE);top.animate().alpha(chrome[0]?1f:0f).translationY(chrome[0]?0:-dp(12)).setDuration(1800).setInterpolator(new DecelerateInterpolator()).start();reply.animate().alpha(chrome[0]?1f:0f).translationY(chrome[0]?0:dp(12)).setDuration(1800).setInterpolator(new DecelerateInterpolator()).start();});image.setOnDismissListener(d::dismiss);d.setContentView(overlay);d.show();Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}}
    private static final class ZoomImageView extends ImageView{
        interface SingleTap{void tap();} interface Dismiss{void dismiss();}
        private SingleTap single; private Dismiss dismiss; private final Matrix matrix=new Matrix();
        private float baseScale=1f,userScale=1f,panX=0,panY=0,startX,startY,lastX,lastY;
        private float pinchDistance=0,pinchScale=1,pinchMidX=0,pinchMidY=0,pinchBaseX=0,pinchBaseY=0;
        private boolean pinching=false,moved=false;private long downAt,lastTap;
        ZoomImageView(Context c){super(c);setClickable(true);setScaleType(ScaleType.MATRIX);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        void setOnSingleTapListener(SingleTap s){single=s;} void setOnDismissListener(Dismiss d){dismiss=d;}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);post(()->applyMatrix(false));}
        @Override public void setImageDrawable(android.graphics.drawable.Drawable d){super.setImageDrawable(d);post(()->{userScale=1f;panX=panY=0;applyMatrix(false);});}
        private float dist(MotionEvent e){if(e.getPointerCount()<2)return 0;float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return(float)Math.hypot(dx,dy);}
        private float midX(MotionEvent e){return(e.getX(0)+e.getX(1))/2f;}private float midY(MotionEvent e){return(e.getY(0)+e.getY(1))/2f;}
        private void dimensions(float[] out){android.graphics.drawable.Drawable d=getDrawable();if(d==null||d.getIntrinsicWidth()<=0||d.getIntrinsicHeight()<=0||getWidth()<=0||getHeight()<=0){out[0]=out[1]=1;return;}float iw=d.getIntrinsicWidth(),ih=d.getIntrinsicHeight();baseScale=Math.min(getWidth()/iw,getHeight()/ih);out[0]=iw*baseScale;out[1]=ih*baseScale;}
        private void clamp(){if(userScale<=1.001f){panX=0;if(panY<0)panY=0;return;}float[] d=new float[2];dimensions(d);float sw=d[0]*userScale,sh=d[1]*userScale;float maxX=Math.max(0,(sw-getWidth())/2f),maxY=Math.max(0,(sh-getHeight())/2f);panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));}
        private void applyMatrix(boolean animated){android.graphics.drawable.Drawable d=getDrawable();if(d==null||getWidth()<=0||getHeight()<=0)return;clamp();float iw=d.getIntrinsicWidth(),ih=d.getIntrinsicHeight();float fit=Math.min(getWidth()/iw,getHeight()/ih);float total=fit*userScale;float dx=(getWidth()-iw*total)/2f+panX,dy=(getHeight()-ih*total)/2f+panY;Matrix target=new Matrix();target.setScale(total,total);target.postTranslate(dx,dy);if(!animated){matrix.set(target);setImageMatrix(matrix);return;}final Matrix from=new Matrix(getImageMatrix());final float[] a=new float[9],b=new float[9];from.getValues(a);target.getValues(b);android.animation.ValueAnimator va=android.animation.ValueAnimator.ofFloat(0f,1f);va.setDuration(220);va.setInterpolator(new DecelerateInterpolator());va.addUpdateListener(x->{float f=(float)x.getAnimatedValue();float[] v=new float[9];for(int i=0;i<9;i++)v[i]=a[i]+(b[i]-a[i])*f;matrix.setValues(v);setImageMatrix(matrix);});va.start();}
        private void reset(boolean anim){userScale=1f;panX=0;panY=0;applyMatrix(anim);}
        @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downAt=System.currentTimeMillis();startX=lastX=e.getX();startY=lastY=e.getY();moved=false;pinching=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN:if(e.getPointerCount()>=2){pinching=true;pinchDistance=Math.max(1f,dist(e));pinchScale=userScale;pinchMidX=midX(e);pinchMidY=midY(e);pinchBaseX=panX;pinchBaseY=panY;moved=true;}return true;
            case MotionEvent.ACTION_MOVE:
                if(pinching&&e.getPointerCount()>=2){float ratio=dist(e)/Math.max(1f,pinchDistance);userScale=Math.max(1f,Math.min(12f,pinchScale*ratio));panX=pinchBaseX+(midX(e)-pinchMidX);panY=pinchBaseY+(midY(e)-pinchMidY);clamp();applyMatrix(false);moved=true;return true;}
                float dx=e.getX()-lastX,dy=e.getY()-lastY;if(Math.hypot(e.getX()-startX,e.getY()-startY)>6)moved=true;if(userScale>1.01f){panX+=dx;panY+=dy;clamp();applyMatrix(false);}else{panY=Math.max(0,e.getY()-startY);applyMatrix(false);}lastX=e.getX();lastY=e.getY();return true;
            case MotionEvent.ACTION_POINTER_UP:if(e.getPointerCount()<=2){pinching=false;int remain=e.getActionIndex()==0?1:0;if(remain<e.getPointerCount()){lastX=e.getX(remain);lastY=e.getY(remain);startX=lastX;startY=lastY;}}clamp();applyMatrix(false);return true;
            case MotionEvent.ACTION_UP:{long now=System.currentTimeMillis();float totalDx=e.getX()-startX,totalDy=e.getY()-startY;if(userScale<=1.01f&&totalDy>dpStatic(this,86)&&Math.abs(totalDy)>Math.abs(totalDx)*1.15f){if(dismiss!=null)dismiss.dismiss();return true;}if(userScale<=1.01f&&panY!=0)reset(true);if(!moved&&!pinching&&now-downAt<350){if(lastTap>0&&now-lastTap<320){lastTap=0;if(userScale>1.01f)reset(true);else{userScale=2.35f;panX=panY=0;applyMatrix(true);}}else{lastTap=now;postDelayed(()->{if(lastTap!=0){lastTap=0;if(single!=null)single.tap();}},320);}}else{clamp();applyMatrix(false);}pinching=false;return true;}
            case MotionEvent.ACTION_CANCEL:if(userScale<=1.01f)reset(true);else{clamp();applyMatrix(false);}pinching=false;return true;}return true;}
        private static int dpStatic(View v,float n){return Math.round(n*v.getResources().getDisplayMetrics().density);}
    }

    private String fileSize(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.US,"%.1f KB",n/1024f);return String.format(Locale.US,"%.1f MB",n/1048576f);}
    private static final class LockView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        LockView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float d=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.48f;
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(246,246,247));p.setShadowLayer(2.2f*d,0,.7f*d,0x22000000);c.drawCircle(cx,cy,r,p);p.clearShadowLayer();
            p.setColor(Color.rgb(158,162,168));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.25f*d);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            float left=cx-6.1f*d,right=cx+6.1f*d,top=cy-1.2f*d,bottom=cy+7.3f*d;c.drawRoundRect(left,top,right,bottom,1.8f*d,1.8f*d,p);
            Path shackle=new Path();shackle.moveTo(cx-4.8f*d,top);shackle.lineTo(cx-4.8f*d,cy-5.4f*d);shackle.cubicTo(cx-4.8f*d,cy-10.1f*d,cx+4.0f*d,cy-10.3f*d,cx+4.0f*d,cy-5.3f*d);c.drawPath(shackle,p);
        }
    }

    private static final class GroupSelectView extends View{
        private boolean selected; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        GroupSelectView(Context c,boolean s){super(c);selected=s;}
        void setSelectedState(boolean s){selected=s;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float d=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f;
            float side=Math.min(getWidth(),getHeight())*.50f,l=cx-side/2f,t=cy-side/2f,r=cx+side/2f,b=cy+side/2f,rad=2.6f*d;
            p.setStyle(Paint.Style.FILL);p.setColor(selected?Color.rgb(91,167,255):Color.TRANSPARENT);canvas.drawRoundRect(l,t,r,b,rad,rad,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.15f*d);p.setColor(selected?Color.rgb(91,167,255):Color.rgb(174,179,187));canvas.drawRoundRect(l,t,r,b,rad,rad,p);
        }
    }
    private static final class DrawOverlay extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Path> paths=new ArrayList<>();
        private final List<Integer> colors=new ArrayList<>();
        private final List<Float> widths=new ArrayList<>();
        private final List<Integer> styles=new ArrayList<>();
        private Path current;
        private int brushStyle=0,currentColor=Color.WHITE;
        private float currentWidth=8f;

        DrawOverlay(Context c){
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            setBackgroundColor(Color.TRANSPARENT);
        }

        int getBrushStyle(){return brushStyle;}
        void setDrawColor(int c){currentColor=c;invalidate();}
        void setStrokeWidth(float w){currentWidth=Math.max(2f,w);}
        void setBrushStyle(int style){brushStyle=style;}

        void undo(){
            int n=paths.size();
            if(n>0){
                paths.remove(n-1);
                colors.remove(n-1);
                widths.remove(n-1);
                styles.remove(n-1);
                invalidate();
            }
        }

        private void configure(Paint p,int style,int color,float width){
            p.reset();
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(width);
            p.setColor(color);
            p.setAlpha(255);
            p.setXfermode(null);
            p.clearShadowLayer();

            switch(style){
                case 1:
                    // Soft translucent spray/highlighter.
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setAlpha(95);
                    p.setStrokeWidth(width*1.35f);
                    break;

                case 2:
                    // Hard marker/glow.
                    p.setStrokeCap(Paint.Cap.SQUARE);
                    p.setShadowLayer(Math.max(5f,width*.8f),0,0,color);
                    break;

                case 3:
                    // Real eraser: CLEAR is replayed with this stroke forever.
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(width*1.55f);
                    p.setXfermode(
                        new android.graphics.PorterDuffXfermode(
                            android.graphics.PorterDuff.Mode.CLEAR
                        )
                    );
                    break;

                default:
                    // Standard round brush.
                    p.setStrokeCap(Paint.Cap.ROUND);
                    break;
            }
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            for(int i=0;i<paths.size();i++){
                configure(
                    paint,
                    styles.get(i),
                    colors.get(i),
                    widths.get(i)
                );
                c.drawPath(paths.get(i),paint);
            }
            paint.setXfermode(null);
        }

        Bitmap exportBitmap(){
            try{
                Bitmap b=Bitmap.createBitmap(
                    Math.max(1,getWidth()),
                    Math.max(1,getHeight()),
                    Bitmap.Config.ARGB_8888
                );
                draw(new Canvas(b));
                return b;
            }catch(Exception e){
                return null;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    current=new Path();
                    current.moveTo(e.getX(),e.getY());
                    paths.add(current);
                    colors.add(currentColor);
                    widths.add(currentWidth);
                    styles.add(brushStyle);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if(current!=null){
                        current.lineTo(e.getX(),e.getY());
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    current=null;
                    invalidate();
                    return true;
            }
            return true;
        }
    }

}
