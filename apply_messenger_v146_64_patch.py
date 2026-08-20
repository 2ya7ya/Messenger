from pathlib import Path
import re

p=Path("app/src/main/java/com/facebook/messengerclone/MainActivity.java")
if not p.exists():
    raise SystemExit("Run this from the Messenger Android project root.")

s=p.read_text(encoding="utf-8")
orig=s

composer_marker='        composer.addView(pill,outerLp);'
if composer_marker not in s:
    raise SystemExit("composer pill marker not found.")
s=s.replace(
    composer_marker,
    '''        composer.addView(pill,outerLp);

        View.OnClickListener openKeyboardFromComposer=v->closePickersAndOpenKeyboard();
        pill.setClickable(true);
        pill.setOnClickListener(openKeyboardFromComposer);
        composer.setClickable(true);
        composer.setOnClickListener(openKeyboardFromComposer);''',
    1
)

old='messageInput.setOnClickListener(v->closePickersAndOpenKeyboard());'
new='''messageInput.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                closePickersOnly();
            }else if(e.getActionMasked()==MotionEvent.ACTION_UP){
                closePickersAndOpenKeyboard();
            }
            return false;
        });
        messageInput.setOnClickListener(v->closePickersAndOpenKeyboard());'''
if old not in s:
    raise SystemExit("v63 messageInput click handler not found.")
s=s.replace(old,new,1)

s=s.replace(
    'sendButton=icon(R.drawable.msg_send_enabled,38,Color.WHITE);',
    'sendButton=icon(R.drawable.msg_send_enabled,46,Color.WHITE);',
    1
)
s=s.replace(
    'sendButton.setBackground(bg(Color.rgb(98,55,255),19));',
    'sendButton.setBackground(bg(Color.rgb(98,55,255),23));',
    1
)
s=s.replace(
    'new LinearLayout.LayoutParams(dp(38),dp(38))',
    'new LinearLayout.LayoutParams(dp(48),dp(38))',
    1
)

s=s.replace(
    'ImageButton sendSelected=icon(R.drawable.msg_send_enabled,42,Color.WHITE);',
    'ImageButton sendSelected=icon(R.drawable.msg_send_enabled,34,Color.WHITE);',
    1
)
s=s.replace(
    'sendSelected.setBackground(bg(Color.rgb(98,55,255),21));',
    'sendSelected.setBackground(bg(Color.rgb(98,55,255),18));',
    1
)
s=s.replace(
    'new LinearLayout.LayoutParams(dp(42),dp(42));',
    'new LinearLayout.LayoutParams(dp(50),dp(36));',
    1
)

field_anchor='    private boolean composerHasText=false, typingStateSent=false, typingStartQueued=false;'
if field_anchor not in s:
    raise SystemExit("field anchor not found.")
s=s.replace(
    field_anchor,
    '''    private boolean composerHasText=false, typingStateSent=false, typingStartQueued=false;
    private boolean forceFullScreenMediaPicker=false;''',
    1
)

helper_anchor='    private boolean hasInstagramMediaPermission(){'
if helper_anchor not in s:
    raise SystemExit("media permission helper anchor not found.")
s=s.replace(
    helper_anchor,
    '''    private void pickInstagramMediaFullScreen(){
        forceFullScreenMediaPicker=true;
        pickInstagramMedia();
    }

'''+helper_anchor,
    1
)

sheet_height_pat=re.compile(
    r'final int sheetH=Math\.min\(\s*dp\(500\),\s*\(int\)\(getResources\(\)\.getDisplayMetrics\(\)\.heightPixels\*\.58f\)\s*\);',
    re.S
)
m=sheet_height_pat.search(s)
if not m:
    raise SystemExit("media picker sheet height block not found.")
s=s[:m.start()]+'''final boolean fullScreenPicker=forceFullScreenMediaPicker;
        forceFullScreenMediaPicker=false;
        final int sheetH=fullScreenPicker
            ?getResources().getDisplayMetrics().heightPixels-dp(18)
            :Math.min(
                dp(500),
                (int)(getResources().getDisplayMetrics().heightPixels*.58f)
            );'''+s[m.end():]

s=s.replace(
    'gallery.setOnClickListener(v->{d.dismiss();pickInstagramMedia();});',
    'gallery.setOnClickListener(v->{d.dismiss();pickInstagramMediaFullScreen();});',
    1
)

old_layout='''FrameLayout.LayoutParams ap=
            new FrameLayout.LayoutParams(dp(60),dp(60),Gravity.START|Gravity.CENTER_VERTICAL);
        ap.leftMargin=dp(16);'''
new_layout='''FrameLayout.LayoutParams ap=
            new FrameLayout.LayoutParams(dp(60),dp(60),Gravity.START|Gravity.TOP);
        ap.leftMargin=dp(16);
        ap.topMargin=dp(250);'''
if old_layout not in s:
    raise SystemExit("camera Aa layout block not found.")
s=s.replace(old_layout,new_layout,1)

s=s.replace(
    'ImageButton gallery=icon(R.drawable.ic_instagram_gallery,46,Color.WHITE);',
    'ImageButton gallery=icon(R.drawable.ic_camera_gallery_ref,46,Color.WHITE);',
    1
)
s=s.replace(
    'ImageButton flash=icon(R.drawable.ic_camera_flash,46,Color.WHITE);',
    'ImageButton flash=icon(R.drawable.ic_camera_flash_off_ref,46,Color.WHITE);',
    1
)

flash_start=s.find('        flash.setOnClickListener(v->{')
if flash_start<0:
    raise SystemExit("camera flash click block not found.")
flash_end=s.find('        });',flash_start)
if flash_end<0:
    raise SystemExit("camera flash click end not found.")
flash_end+=len('        });')
flash_new='''        flash.setOnClickListener(v->{
            try{
                if(cam[0]==null)return;
                android.hardware.Camera.Parameters params=cam[0].getParameters();
                List<String> modes=params.getSupportedFlashModes();
                if(modes==null)return;

                flashOn[0]=!flashOn[0];
                String mode=flashOn[0]
                    ?android.hardware.Camera.Parameters.FLASH_MODE_TORCH
                    :android.hardware.Camera.Parameters.FLASH_MODE_OFF;

                if(modes.contains(mode)){
                    params.setFlashMode(mode);
                    cam[0].setParameters(params);
                }

                flash.setImageResource(
                    flashOn[0]
                        ?R.drawable.ic_camera_flash_on_ref
                        :R.drawable.ic_camera_flash_off_ref
                );
                flash.setColorFilter(
                    flashOn[0]?Color.rgb(255,221,64):Color.WHITE
                );
                flash.setBackground(
                    flashOn[0]
                        ?bg(Color.argb(105,255,221,64),23)
                        :new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                );
            }catch(Exception ignored){}
        });'''
s=s[:flash_start]+flash_new+s[flash_end:]

surface_marker='        surface.getHolder().addCallback(new android.view.SurfaceHolder.Callback(){'
if surface_marker not in s:
    raise SystemExit("camera surface callback marker not found.")
swipe_code='''        final float[] cameraSwipeDownY={Float.NaN};
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
                        if(dy<-dp(72)){
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

'''
s=s.replace(surface_marker,swipe_code+surface_marker,1)

old_capture='''cam[0].takePicture(null,null,(data,c)->{
                    if(data==null)return;
                    byte[] bytes=data.clone();
                    d.dismiss();
                    main.post(()->uploadAttachment(
                        bytes,
                        "camera-"+System.currentTimeMillis()+".jpg",
                        "image/jpeg"
                    ));
                });'''
new_capture='''cam[0].takePicture(null,null,(data,c)->{
                    if(data==null)return;
                    byte[] bytes=data.clone();
                    d.dismiss();
                    main.post(()->showCapturedMediaPreview(bytes));
                });'''
if old_capture not in s:
    raise SystemExit("camera immediate-send capture block not found.")
s=s.replace(old_capture,new_capture,1)

insert_anchor='    private void showMessageTextCreate(){'
if insert_anchor not in s:
    raise SystemExit("text create method anchor not found.")

preview_methods=r'''    private void showCapturedMediaPreview(final byte[] bytes){
        if(bytes==null||bytes.length==0)return;

        final Dialog d=new Dialog(
            this,
            android.R.style.Theme_Black_NoTitleBar_Fullscreen
        );

        final FrameLayout page=new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);

        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));
        photo.setBackground(bg(Color.BLACK,22));
        photo.setClipToOutline(true);

        FrameLayout.LayoutParams photoLp=
            new FrameLayout.LayoutParams(-1,0,Gravity.TOP);
        photoLp.topMargin=dp(54);
        photoLp.height=getResources().getDisplayMetrics().heightPixels-dp(220);
        page.addView(photo,photoLp);

        ImageButton back=icon(R.drawable.ic_camera_back_ref,48,Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams backLp=
            new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.START);
        backLp.leftMargin=dp(12);
        backLp.topMargin=dp(74);
        page.addView(back,backLp);
        back.setOnClickListener(v->d.dismiss());

        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView aa=text("Aa",26,Color.WHITE,Typeface.NORMAL);
        aa.setGravity(Gravity.CENTER);
        aa.setBackground(bg(Color.rgb(36,37,41),28));
        tools.addView(aa,new LinearLayout.LayoutParams(dp(56),dp(56)));

        ImageButton sticker=icon(R.drawable.ic_camera_sticker_ref,56,Color.WHITE);
        sticker.setBackground(bg(Color.rgb(36,37,41),28));
        LinearLayout.LayoutParams toolLp1=new LinearLayout.LayoutParams(dp(56),dp(56));
        toolLp1.topMargin=dp(8);
        tools.addView(sticker,toolLp1);

        ImageButton draw=icon(R.drawable.ic_camera_draw_ref,56,Color.WHITE);
        draw.setBackground(bg(Color.rgb(36,37,41),28));
        LinearLayout.LayoutParams toolLp2=new LinearLayout.LayoutParams(dp(56),dp(56));
        toolLp2.topMargin=dp(8);
        tools.addView(draw,toolLp2);

        ImageButton download=icon(R.drawable.ic_camera_download_ref,56,Color.WHITE);
        download.setBackground(bg(Color.rgb(36,37,41),28));
        LinearLayout.LayoutParams toolLp3=new LinearLayout.LayoutParams(dp(56),dp(56));
        toolLp3.topMargin=dp(8);
        tools.addView(download,toolLp3);

        FrameLayout.LayoutParams toolsLp=
            new FrameLayout.LayoutParams(dp(64),-2,Gravity.TOP|Gravity.END);
        toolsLp.rightMargin=dp(12);
        toolsLp.topMargin=dp(76);
        page.addView(tools,toolsLp);

        download.setOnClickListener(v->{
            try{
                android.provider.MediaStore.Images.Media.insertImage(
                    getContentResolver(),
                    BitmapFactory.decodeByteArray(bytes,0,bytes.length),
                    "Messenger-"+System.currentTimeMillis(),
                    "Messenger photo"
                );
                toast("Saved");
            }catch(Exception e){
                toast("Couldn't save photo.");
            }
        });

        final int[] viewMode={2};

        TextView viewModeButton=text("◌²  View twice",18,Color.WHITE,Typeface.BOLD);
        viewModeButton.setGravity(Gravity.CENTER);
        viewModeButton.setBackground(bg(Color.rgb(34,35,39),27));
        FrameLayout.LayoutParams viewLp=
            new FrameLayout.LayoutParams(dp(260),dp(58),Gravity.BOTTOM|Gravity.START);
        viewLp.leftMargin=dp(18);
        viewLp.bottomMargin=dp(24);
        page.addView(viewModeButton,viewLp);
        viewModeButton.setOnClickListener(v->
            showCapturedViewModeMenu(viewModeButton,viewMode)
        );

        LinearLayout sendPill=new LinearLayout(this);
        sendPill.setGravity(Gravity.CENTER_VERTICAL);
        sendPill.setPadding(dp(7),0,dp(15),0);
        sendPill.setBackground(bg(Color.WHITE,28));

        ImageView avatar=buildUserAvatar(
            activeConversation==null?new JSONObject():activeConversation,
            38
        );
        sendPill.addView(avatar,new LinearLayout.LayoutParams(dp(38),dp(38)));

        TextView sendText=text("Send",17,Color.rgb(35,35,35),Typeface.BOLD);
        LinearLayout.LayoutParams sendTextLp=
            new LinearLayout.LayoutParams(-2,dp(48));
        sendTextLp.leftMargin=dp(8);
        sendPill.addView(sendText,sendTextLp);

        FrameLayout.LayoutParams sendLp=
            new FrameLayout.LayoutParams(dp(150),dp(58),Gravity.BOTTOM|Gravity.END);
        sendLp.rightMargin=dp(18);
        sendLp.bottomMargin=dp(24);
        page.addView(sendPill,sendLp);

        sendPill.setOnClickListener(v->{
            d.dismiss();
            uploadAttachment(
                bytes,
                "camera-"+System.currentTimeMillis()+".jpg",
                "image/jpeg"
            );
        });

        d.setContentView(page);
        d.show();

        Window w=d.getWindow();
        if(w!=null){
            w.setLayout(-1,-1);
            w.setStatusBarColor(Color.BLACK);
            w.setNavigationBarColor(Color.BLACK);
        }
    }

    private void showCapturedViewModeMenu(
        final TextView anchor,
        final int[] mode
    ){
        final android.widget.PopupWindow popup=
            new android.widget.PopupWindow(this);

        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22),dp(18),dp(22),dp(18));
        card.setBackground(bg(Color.rgb(40,42,48),18));

        TextView help=text(
            "Set how many times this\\nphoto can be viewed.",
            14,
            Color.rgb(178,183,194),
            Typeface.NORMAL
        );
        help.setGravity(Gravity.START);
        card.addView(help,new LinearLayout.LayoutParams(dp(280),dp(60)));

        String[] labels={"View once","View twice","Unlimited\\nviews"};
        int[] values={1,2,0};

        for(int i=0;i<labels.length;i++){
            final int value=values[i];
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView icon=text(
                value==0?"✓":"◌"+(value==1?"¹":"²"),
                24,
                Color.WHITE,
                Typeface.NORMAL
            );
            icon.setGravity(Gravity.CENTER);
            row.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(66)));

            TextView label=text(labels[i],18,Color.WHITE,Typeface.NORMAL);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(66),1));

            TextView check=text(
                mode[0]==value?"✓":"",
                22,
                Color.WHITE,
                Typeface.NORMAL
            );
            check.setGravity(Gravity.CENTER);
            row.addView(check,new LinearLayout.LayoutParams(dp(34),dp(66)));

            card.addView(row,new LinearLayout.LayoutParams(dp(290),dp(66)));

            row.setOnClickListener(v->{
                mode[0]=value;
                anchor.setText(
                    value==1
                        ?"◌¹  View once"
                        :value==2
                            ?"◌²  View twice"
                            :"✓  Unlimited views"
                );
                popup.dismiss();
            });
        }

        popup.setContentView(card);
        popup.setWidth(dp(334));
        popup.setHeight(-2);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        );
        popup.setElevation(dp(12));
        popup.showAsDropDown(anchor,0,-dp(350));
    }

'''
s=s.replace(insert_anchor,preview_methods+insert_anchor,1)

if s==orig:
    raise SystemExit("No changes made.")

p.write_text(s,encoding="utf-8")
print("Messenger v64 patch applied successfully.")
