#!/usr/bin/env python3
from pathlib import Path
import re, shutil

ROOT = Path.cwd()
MAIN = ROOT / "app/src/main/java/com/facebook/messengerclone/MainActivity.java"

if not MAIN.exists():
    raise SystemExit(
        "MainActivity.java was not found.\n"
        "Run this script from the root of your latest Messenger source repo."
    )

text = MAIN.read_text(encoding="utf-8")

# This patch is intentionally narrow: it only changes
# 1) keyboard/composer protection,
# 2) direct Facebook -> conversation handoff,
# 3) theme names/list to match the v146 website.

# ------------------------------------------------------------------
# 1) DIRECT FACEBOOK -> CONVERSATION HANDOFF (NO CHATS PAGE FLASH)
# ------------------------------------------------------------------

old_oncreate = (
    'if(api.hasSession()){showInbox(true);main.post(this::openPendingExternalConversation);}else showLogin();}'
)
new_oncreate = (
    'if(api.hasSession()){'
    'if(pendingExternalUserId!=null&&!pendingExternalUserId.isEmpty()){'
    'showDirectConversationLoading();main.post(this::openPendingExternalConversation);'
    '}else showInbox(true);'
    '}else showLogin();}'
)
if old_oncreate not in text:
    raise SystemExit("Expected current onCreate handoff code was not found; refusing to patch an unknown/older source.")
text = text.replace(old_oncreate, new_oncreate, 1)

old_onnew = (
    'if(api!=null&&api.hasSession()){if(activeConversation==null)showInbox(false);'
    'main.post(this::openPendingExternalConversation);}}'
)
new_onnew = (
    'if(api!=null&&api.hasSession()){'
    'if(pendingExternalUserId!=null&&!pendingExternalUserId.isEmpty()){'
    'showDirectConversationLoading();main.post(this::openPendingExternalConversation);'
    '}else if(activeConversation==null)showInbox(false);'
    '}}'
)
if old_onnew not in text:
    raise SystemExit("Expected current onNewIntent handoff code was not found; refusing to patch an unknown/older source.")
text = text.replace(old_onnew, new_onnew, 1)

old_login_success = 'showInbox(true);main.post(this::openPendingExternalConversation);'
new_login_success = (
    'if(pendingExternalUserId!=null&&!pendingExternalUserId.isEmpty()){'
    'showDirectConversationLoading();main.post(this::openPendingExternalConversation);'
    '}else showInbox(true);'
)
if old_login_success not in text:
    raise SystemExit("Expected current login-success handoff code was not found.")
text = text.replace(old_login_success, new_login_success, 1)

direct_block_pattern = re.compile(
    r'    private void openPendingExternalConversation\(\)\{.*?\n    \}\n'
    r'    private void installImeSafety\(\)\{',
    re.S
)
direct_replacement = r'''    private void showDirectConversationLoading(){
        activeConversation=null;replyTo=null;
        if(root==null)return;
        root.removeAllViews();
        FrameLayout loading=new FrameLayout(this);
        loading.setBackgroundColor(Color.WHITE);
        ProgressBar spinner=new ProgressBar(this);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER);
        loading.addView(spinner,sp);
        root.addView(loading,new FrameLayout.LayoutParams(-1,-1));
    }

    private void openPendingExternalConversation(){
        if(api==null||!api.hasSession()||pendingExternalUserId==null||pendingExternalUserId.isEmpty())return;
        final String target=pendingExternalUserId;pendingExternalUserId="";
        try{
            api.post("/api/messaging/conversations",
                new JSONObject().put("type","direct").put("userId",target),
                (json,error)->main.post(()->{
                    if(error!=null){
                        pendingExternalUserId=target;
                        showInbox(false);
                        toast(error.getMessage());
                        return;
                    }
                    JSONObject conversation=json.optJSONObject("conversation");
                    if(conversation!=null){
                        openConversation(conversation);
                        main.postDelayed(()->{
                            if(activeConversation!=null&&conversation.optString("id").equals(activeConversation.optString("id")))
                                scrollToAbsoluteBottom();
                        },120);
                    }else{
                        pendingExternalUserId=target;
                        showInbox(false);
                    }
                })
            );
        }catch(Exception e){
            pendingExternalUserId=target;
            showInbox(false);
            toast(e.getMessage());
        }
    }

    private void installImeSafety(){'''
text, n = direct_block_pattern.subn(direct_replacement, text, count=1)
if n != 1:
    raise SystemExit("Could not locate the current direct-conversation/IME block.")

# ------------------------------------------------------------------
# 2) MESSAGE BAR MUST ALWAYS STAY ABOVE THE KEYBOARD
# ------------------------------------------------------------------

ime_pattern = re.compile(
    r'    private void installImeSafety\(\)\{.*?\n    \}\n'
    r'    @Override protected void onResume',
    re.S
)

ime_replacement = r'''    private void installImeSafety(){
        if(root==null)return;

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if(Build.VERSION.SDK_INT>=30){
            try{getWindow().setDecorFitsSystemWindows(true);}catch(Throwable ignored){}
            root.setOnApplyWindowInsetsListener((v,insets)->{
                main.post(this::applyImeSafetyNow);
                return v.onApplyWindowInsets(insets);
            });
        }

        imeSafetyListener=()->main.post(this::applyImeSafetyNow);
        root.getViewTreeObserver().addOnGlobalLayoutListener(imeSafetyListener);
    }

    private void applyImeSafetyNow(){
        if(root==null||composer==null||activeConversation==null)return;
        if(root.findViewWithTag("messenger-native-media-sheet")!=null||
           root.findViewWithTag("messenger-native-sticker-sheet")!=null)return;

        View decor=getWindow().getDecorView();
        Rect visible=new Rect();
        decor.getWindowVisibleDisplayFrame(visible);

        int[] composerLoc=new int[2];
        composer.getLocationOnScreen(composerLoc);

        int naturalComposerBottom=Math.round(
            composerLoc[1]+composer.getHeight()-composer.getTranslationY()
        );

        int keyboardTopFromVisible=visible.bottom;
        int keyboardTopFromInsets=Integer.MAX_VALUE;

        if(Build.VERSION.SDK_INT>=30){
            try{
                android.view.WindowInsets wi=root.getRootWindowInsets();
                if(wi!=null){
                    android.graphics.Insets ime=
                        wi.getInsets(android.view.WindowInsets.Type.ime());
                    if(ime.bottom>dp(40)){
                        int[] decorLoc=new int[2];
                        decor.getLocationOnScreen(decorLoc);
                        keyboardTopFromInsets=
                            decorLoc[1]+decor.getHeight()-ime.bottom;
                    }
                }
            }catch(Throwable ignored){}
        }

        int keyboardTop=Math.min(keyboardTopFromVisible,keyboardTopFromInsets);
        int lift=Math.max(0,naturalComposerBottom-keyboardTop+dp(2));
        if(lift<dp(4))lift=0;

        float target=-lift;
        if(Math.abs(composer.getTranslationY()-target)>.5f)
            composer.setTranslationY(target);

        if(replyBar!=null&&replyBar.getVisibility()==View.VISIBLE&&
           Math.abs(replyBar.getTranslationY()-target)>.5f)
            replyBar.setTranslationY(target);

        if(recordBar!=null&&recordBar.getVisibility()==View.VISIBLE&&
           Math.abs(recordBar.getTranslationY()-target)>.5f)
            recordBar.setTranslationY(target);

        if(list!=null){
            int bottom=dp(26)+lift;
            if(list.getPaddingBottom()!=bottom){
                list.setPadding(
                    list.getPaddingLeft(),
                    list.getPaddingTop(),
                    list.getPaddingRight(),
                    bottom
                );
            }
        }
    }

    @Override protected void onResume'''
text, n = ime_pattern.subn(ime_replacement, text, count=1)
if n != 1:
    raise SystemExit("Could not replace the current IME safety implementation.")

# ------------------------------------------------------------------
# 3) THEME NAMES / LIST = EXACT WEBSITE NAMES
# ------------------------------------------------------------------

theme_label_pattern = re.compile(
    r'    private String themeLabel\(String key\)\{.*?\}\n'
    r'    private void showThemePage',
    re.S
)

theme_label_replacement = r'''    private String themeLabel(String key){
        String k=key==null?"default":key;
        String[] keys={
            "default","instagram","love","ocean","sunset","monochrome",
            "glow-pup","odyssey","supergirl","avatar","olivia",
            "backrooms","deli-boys","heart-drive","valentines"
        };
        String[] labels={
            "Default","Instagram","Love","Ocean","Sunset","Monochrome",
            "Glow Pup","The Odyssey","Supergirl","Avatar: The Last Airbender",
            "Olivia Rodrigo","Backrooms","Deli Boys","Heart Drive",
            "Valentine’s Day"
        };
        for(int i=0;i<keys.length;i++)if(keys[i].equals(k))return labels[i];
        if("instagram-classic".equals(k))return "Instagram";
        return "Default";
    }

    private void showThemePage'''
text, n = theme_label_pattern.subn(theme_label_replacement, text, count=1)
if n != 1:
    raise SystemExit("Could not locate themeLabel().")

show_theme_start = text.find("    private void showThemePage(JSONObject c)")
if show_theme_start < 0:
    raise SystemExit("Could not locate showThemePage().")
show_theme_end = text.find("    private void showNicknames(", show_theme_start)
if show_theme_end < 0:
    raise SystemExit("Could not locate the end of showThemePage().")

theme_section = text[show_theme_start:show_theme_end]
theme_keys_pattern = re.compile(r'String\[\] keys=\{.*?\};')
website_keys = (
    'String[] keys={"default","instagram","love","ocean","sunset","monochrome",'
    '"glow-pup","odyssey","supergirl","avatar","olivia","backrooms",'
    '"deli-boys","heart-drive","valentines"};'
)
theme_section, n = theme_keys_pattern.subn(website_keys, theme_section, count=1)
if n != 1:
    raise SystemExit("Could not update the theme picker key list.")
text = text[:show_theme_start] + theme_section + text[show_theme_end:]

required = [
    "showDirectConversationLoading()",
    "applyImeSafetyNow()",
    '"Avatar: The Last Airbender"',
    '"Valentine’s Day"',
    'String[] keys={"default","instagram","love","ocean","sunset","monochrome"',
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"Validation failed: missing {marker}")

if 'showInbox(true);main.post(this::openPendingExternalConversation);' in text:
    raise SystemExit("Validation failed: old Chats-first handoff still exists.")

backup = MAIN.with_suffix(".java.before_latest_three_fixes")
if not backup.exists():
    shutil.copy2(MAIN, backup)

MAIN.write_text(text, encoding="utf-8")
print("Applied successfully:")
print("  ✓ message bar stays above the keyboard")
print("  ✓ exact website theme names/list")
print("  ✓ Facebook Message opens target conversation without Chats first")
print(f"Backup: {backup}")
