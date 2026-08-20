from pathlib import Path

p=Path("app/src/main/java/com/facebook/messengerclone/MainActivity.java")
if not p.exists():
    raise SystemExit("Run this from the Messenger Android project root.")

s=p.read_text(encoding="utf-8")
orig=s

def replace_method(name,new_text,next_anchor,label):
    global s
    start=s.find(name)
    if start<0:
        raise SystemExit("Missing "+label+" start")
    end=s.find(next_anchor,start)
    if end<0:
        raise SystemExit("Missing "+label+" end")
    s=s[:start]+new_text+s[end:]

# Camera gallery thumbnail instead of plain icon.
old='ImageButton gallery=icon(R.drawable.ic_ref_gallery,44,Color.WHITE);'
if old in s:
    s=s.replace(old,'''ImageButton gallery=new ImageButton(this);
        gallery.setScaleType(ImageView.ScaleType.CENTER_CROP);
        gallery.setImageResource(R.drawable.ic_ref_gallery);
        gallery.setBackground(bg(Color.argb(140,28,28,28),18));
        gallery.setClipToOutline(true);
        new Thread(()->{
            try{
                Uri collection=android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] projection={android.provider.MediaStore.Images.Media._ID};
                String order=android.provider.MediaStore.Images.Media.DATE_ADDED+" DESC";
                Uri latestUri=null;
                try(android.database.Cursor c=getContentResolver().query(collection,projection,null,null,order)){
                    if(c!=null&&c.moveToFirst()){
                        int idCol=c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID);
                        latestUri=android.content.ContentUris.withAppendedId(collection,c.getLong(idCol));
                    }
                }
                if(latestUri!=null){
                    final Bitmap bm;
                    if(Build.VERSION.SDK_INT>=29){
                        bm=getContentResolver().loadThumbnail(latestUri,new android.util.Size(dp(96),dp(96)),null);
                    }else{
                        bm=BitmapFactory.decodeStream(getContentResolver().openInputStream(latestUri));
                    }
                    if(bm!=null)main.post(()->gallery.setImageBitmap(bm));
                }
            }catch(Exception ignored){}
        }).start();''',1)
else:
    raise SystemExit("Missing camera gallery anchor")

s=s.replace('gallery.setOnClickListener(v->pickInstagramMedia());','gallery.setOnClickListener(v->pickInstagramMediaFullScreen());')
s=s.replace('gallery.setOnClickListener(v -> pickInstagramMedia());','gallery.setOnClickListener(v -> pickInstagramMediaFullScreen());')

# Captured-photo preview page.
preview='''    private void showCapturedMediaPreview(final byte[] bytes){
        if(bytes==null||bytes.length==0)return;

        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout page=new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);

        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));
        photo.setBackground(bg(Color.BLACK,20));
        photo.setClipToOutline(true);

        FrameLayout.LayoutParams photoLp=new FrameLayout.LayoutParams(-1,-1);
        photoLp.topMargin=dp(42);
        photoLp.bottomMargin=dp(86);
        page.addView(photo,photoLp);

        ImageButton back=icon(R.drawable.ic_camera_back_ref,40,Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams backLp=
            new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP|Gravity.START);
        backLp.leftMargin=dp(10);
        backLp.topMargin=dp(54);
        page.addView(back,backLp);
        back.setOnClickListener(v->{
            showMessageCamera();
            d.dismiss();
        });

        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView aa=text("Aa",18,Color.WHITE,Typeface.NORMAL);
        aa.setGravity(Gravity.CENTER);
        aa.setBackground(bg(Color.rgb(36,37,41),18));
        tools.addView(aa,new LinearLayout.LayoutParams(dp(38),dp(38)));
        aa.setOnClickListener(v->{
            d.dismiss();
            showMessageTextCreate(bytes);
        });

        ImageButton sticker=icon(R.drawable.ic_ref_sticker,36,Color.WHITE);
        sticker.clearColorFilter();
        sticker.setBackground(bg(Color.rgb(36,37,41),18));
        LinearLayout.LayoutParams tp1=new LinearLayout.LayoutParams(dp(38),dp(38));
        tp1.topMargin=dp(6);
        tools.addView(sticker,tp1);

        ImageButton draw=icon(R.drawable.ic_ref_draw,36,Color.WHITE);
        draw.clearColorFilter();
        draw.setBackground(bg(Color.rgb(36,37,41),18));
        LinearLayout.LayoutParams tp2=new LinearLayout.LayoutParams(dp(38),dp(38));
        tp2.topMargin=dp(6);
        tools.addView(draw,tp2);

        ImageButton download=icon(R.drawable.ic_camera_download_ref,36,Color.WHITE);
        download.setBackground(bg(Color.rgb(36,37,41),18));
        LinearLayout.LayoutParams tp3=new LinearLayout.LayoutParams(dp(38),dp(38));
        tp3.topMargin=dp(6);
        tools.addView(download,tp3);

        FrameLayout.LayoutParams toolsLp=
            new FrameLayout.LayoutParams(dp(44),-2,Gravity.TOP|Gravity.END);
        toolsLp.rightMargin=dp(10);
        toolsLp.topMargin=dp(66);
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

        LinearLayout viewModeButton=new LinearLayout(this);
        viewModeButton.setGravity(Gravity.CENTER);
        viewModeButton.setPadding(dp(10),0,dp(10),0);
        viewModeButton.setBackground(bg(Color.rgb(34,35,39),22));

        ImageView viewIcon=new ImageView(this);
        viewIcon.setImageResource(R.drawable.ic_ref_view_twice);
        viewIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams vilp=new LinearLayout.LayoutParams(dp(21),dp(21));
        vilp.rightMargin=dp(4);
        viewModeButton.addView(viewIcon,vilp);

        TextView viewLabel=text("View twice",14,Color.WHITE,Typeface.BOLD);
        viewLabel.setGravity(Gravity.CENTER);
        viewModeButton.addView(viewLabel,new LinearLayout.LayoutParams(-2,dp(42)));

        FrameLayout.LayoutParams viewLp=
            new FrameLayout.LayoutParams(dp(146),dp(44),Gravity.BOTTOM|Gravity.START);
        viewLp.leftMargin=dp(14);
        viewLp.bottomMargin=dp(18);
        page.addView(viewModeButton,viewLp);

        viewModeButton.setOnClickListener(v->
            showCapturedViewModeMenu(viewIcon,viewLabel,viewMode)
        );

        LinearLayout sendPill=new LinearLayout(this);
        sendPill.setGravity(Gravity.CENTER);
        sendPill.setPadding(dp(7),0,dp(11),0);
        sendPill.setBackground(bg(Color.WHITE,22));

        View avatar=buildUserAvatar(
            activeConversation==null?"":activeConversation.optString("avatar",""),
            conversationName(),
            26
        );
        sendPill.addView(avatar,new LinearLayout.LayoutParams(dp(26),dp(26)));

        TextView sendText=text("Send",14,Color.rgb(35,35,35),Typeface.BOLD);
        sendText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-2,dp(40));
        stp.leftMargin=dp(6);
        sendPill.addView(sendText,stp);

        FrameLayout.LayoutParams sendLp=
            new FrameLayout.LayoutParams(dp(104),dp(44),Gravity.BOTTOM|Gravity.END);
        sendLp.rightMargin=dp(14);
        sendLp.bottomMargin=dp(18);
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

'''
replace_method('    private void showCapturedMediaPreview(final byte[] bytes){',preview,'    private void showCapturedViewModeMenu(','captured preview')

# View-mode popup menu.
view_menu='''    private void showCapturedViewModeMenu(
        final ImageView anchorIcon,
        final TextView anchorLabel,
        final int[] mode
    ){
        final android.widget.PopupWindow popup=new android.widget.PopupWindow(this);

        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(13),dp(16),dp(13));
        card.setBackground(bg(Color.rgb(40,42,48),16));

        TextView help=text(
            "Set how many times this\\nphoto can be viewed.",
            13,
            Color.rgb(178,183,194),
            Typeface.NORMAL
        );
        card.addView(help,new LinearLayout.LayoutParams(dp(235),dp(50)));

        String[] labels={"View once","View twice","Unlimited views"};
        int[] values={1,2,0};
        int[] icons={
            R.drawable.ic_ref_view_once,
            R.drawable.ic_ref_view_twice,
            R.drawable.ic_ref_unlimited
        };

        for(int i=0;i<labels.length;i++){
            final int value=values[i];
            final int iconRes=icons[i];

            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            ImageView iconView=new ImageView(this);
            iconView.setImageResource(iconRes);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconView.setTranslationY(dp(2));
            row.addView(iconView,new LinearLayout.LayoutParams(dp(25),dp(25)));

            TextView label=text(labels[i],16,Color.WHITE,Typeface.NORMAL);
            LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,dp(54),1);
            llp.leftMargin=dp(9);
            row.addView(label,llp);

            TextView check=text(
                mode[0]==value?"✓":"",
                19,
                Color.WHITE,
                Typeface.NORMAL
            );
            check.setGravity(Gravity.CENTER);
            row.addView(check,new LinearLayout.LayoutParams(dp(28),dp(54)));

            card.addView(row,new LinearLayout.LayoutParams(dp(245),dp(54)));

            row.setOnClickListener(v->{
                mode[0]=value;
                anchorIcon.setImageResource(iconRes);
                anchorLabel.setText(
                    value==1?"View once":
                    value==2?"View twice":"Unlimited"
                );
                popup.dismiss();
            });
        }

        popup.setContentView(card);
        popup.setWidth(dp(277));
        popup.setHeight(-2);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        );
        popup.setElevation(dp(10));
        popup.showAtLocation(anchorLabel,Gravity.BOTTOM|Gravity.START,dp(14),dp(68));
    }

'''
replace_method('    private void showCapturedViewModeMenu(',view_menu,'    private void showMessageTextCreate()','view mode menu')

# Aa page with outlined Aa button and outlined color dot.
aa_method='''    private void showMessageTextCreate(){
        showMessageTextCreate(null);
    }

    private void showMessageTextCreate(final byte[] baseBytes){
        final Dialog d=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final FrameLayout page=new FrameLayout(this);
        final FrameLayout canvas=new FrameLayout(this);
        final int[][] palettes={
            {0xffff315f,0xffff7a18,0xffffd138},
            {0xff833ab4,0xfffd1d1d,0xfffcb045},
            {0xff5b46f6,0xffcd30fa,0xffff508d}
        };
        final int[] ix={0};

        Runnable paint=()->{
            if(baseBytes==null){
                canvas.setBackground(
                    new GradientDrawable(GradientDrawable.Orientation.TL_BR,palettes[ix[0]])
                );
            }
        };
        paint.run();

        FrameLayout.LayoutParams canvasLp=new FrameLayout.LayoutParams(-1,-1);
        canvasLp.topMargin=dp(40);
        canvasLp.bottomMargin=dp(92);
        page.addView(canvas,canvasLp);

        if(baseBytes!=null){
            ImageView base=new ImageView(this);
            base.setScaleType(ImageView.ScaleType.CENTER_CROP);
            base.setImageBitmap(BitmapFactory.decodeByteArray(baseBytes,0,baseBytes.length));
            canvas.addView(base,new FrameLayout.LayoutParams(-1,-1));
        }

        View shade=new View(this);
        shade.setBackgroundColor(Color.argb(55,0,0,0));
        page.addView(shade,new FrameLayout.LayoutParams(-1,dp(40),Gravity.TOP));
        page.addView(messageTitle(),new FrameLayout.LayoutParams(-1,dp(38),Gravity.TOP));

        ImageButton close=icon(R.drawable.ic_msg_close,38,Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams cp=
            new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.TOP|Gravity.START);
        cp.leftMargin=dp(10);
        cp.topMargin=dp(48);
        page.addView(close,cp);
        close.setOnClickListener(v->{
            d.dismiss();
            if(baseBytes!=null)showCapturedMediaPreview(baseBytes);
        });

        final TextView overlay=text("",29,Color.WHITE,Typeface.NORMAL);
        overlay.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams olp=
            new FrameLayout.LayoutParams(-1,dp(200),Gravity.CENTER);
        olp.leftMargin=dp(28);
        olp.rightMargin=dp(28);
        canvas.addView(overlay,olp);

        EditText input=new EditText(this);
        input.setHint("Type a message...");
        input.setHintTextColor(Color.argb(130,255,255,255));
        input.setTextColor(Color.WHITE);
        input.setTextSize(29);
        input.setGravity(Gravity.CENTER);
        input.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams ip=
            new FrameLayout.LayoutParams(-1,dp(200),Gravity.CENTER);
        ip.leftMargin=dp(28);
        ip.rightMargin=dp(28);
        page.addView(input,ip);
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){overlay.setText(s);}
            public void afterTextChanged(Editable e){}
        });

        FrameLayout createWrap=new FrameLayout(this);
        GradientDrawable outerCreate=new GradientDrawable();
        outerCreate.setShape(GradientDrawable.OVAL);
        outerCreate.setColor(Color.TRANSPARENT);
        outerCreate.setStroke(dp(3),Color.WHITE);
        createWrap.setBackground(outerCreate);
        FrameLayout.LayoutParams createWrapLp=
            new FrameLayout.LayoutParams(dp(72),dp(72),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        createWrapLp.bottomMargin=dp(54);
        page.addView(createWrap,createWrapLp);

        TextView create=text("Aa",28,Color.BLACK,Typeface.NORMAL);
        create.setGravity(Gravity.CENTER);
        create.setBackground(bg(Color.WHITE,31));
        FrameLayout.LayoutParams createp=
            new FrameLayout.LayoutParams(dp(62),dp(62),Gravity.CENTER);
        createWrap.addView(create,createp);

        create.setOnClickListener(v->{
            try{
                input.clearFocus();
                page.post(()->{
                    Bitmap bm=Bitmap.createBitmap(
                        canvas.getWidth(),
                        canvas.getHeight(),
                        Bitmap.Config.ARGB_8888
                    );
                    Canvas c=new Canvas(bm);
                    canvas.draw(c);
                    java.io.ByteArrayOutputStream bos=new java.io.ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.JPEG,95,bos);
                    d.dismiss();
                    showCapturedMediaPreview(bos.toByteArray());
                });
            }catch(Exception e){
                toast(e.getMessage());
            }
        });

        final ImageView latest=new ImageView(this);
        latest.setScaleType(ImageView.ScaleType.CENTER_CROP);
        latest.setBackground(bg(Color.argb(90,0,0,0),11));
        latest.setClipToOutline(true);
        FrameLayout.LayoutParams latestLp=
            new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.BOTTOM|Gravity.START);
        latestLp.leftMargin=dp(20);
        latestLp.bottomMargin=dp(18);
        page.addView(latest,latestLp);
        latest.setOnClickListener(v->{
            d.dismiss();
            pickInstagramMediaFullScreen();
        });

        new Thread(()->{
            Uri latestUri=null;
            try{
                Uri collection=android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] projection={android.provider.MediaStore.Images.Media._ID};
                String order=android.provider.MediaStore.Images.Media.DATE_ADDED+" DESC";
                try(android.database.Cursor c=getContentResolver().query(
                    collection,projection,null,null,order
                )){
                    if(c!=null&&c.moveToFirst()){
                        int idCol=c.getColumnIndexOrThrow(
                            android.provider.MediaStore.Images.Media._ID
                        );
                        latestUri=android.content.ContentUris.withAppendedId(
                            collection,c.getLong(idCol)
                        );
                    }
                }
                final Uri found=latestUri;
                if(found!=null){
                    Bitmap bm;
                    if(Build.VERSION.SDK_INT>=29){
                        bm=getContentResolver().loadThumbnail(
                            found,new android.util.Size(dp(90),dp(90)),null
                        );
                    }else{
                        bm=BitmapFactory.decodeStream(
                            getContentResolver().openInputStream(found)
                        );
                    }
                    if(bm!=null)main.post(()->latest.setImageBitmap(bm));
                }
            }catch(Exception ignored){}
        }).start();

        FrameLayout dotWrap=new FrameLayout(this);
        GradientDrawable dotOuter=new GradientDrawable();
        dotOuter.setShape(GradientDrawable.OVAL);
        dotOuter.setColor(Color.TRANSPARENT);
        dotOuter.setStroke(dp(2),Color.WHITE);
        dotWrap.setBackground(dotOuter);
        FrameLayout.LayoutParams dotWrapLp=
            new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.BOTTOM|Gravity.END);
        dotWrapLp.rightMargin=dp(22);
        dotWrapLp.bottomMargin=dp(22);
        page.addView(dotWrap,dotWrapLp);

        GradientDrawable dotBg=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            palettes[0]
        );
        dotBg.setShape(GradientDrawable.OVAL);
        View dot=new View(this);
        dot.setBackground(dotBg);
        FrameLayout.LayoutParams dpv=
            new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER);
        dotWrap.addView(dot,dpv);
        dotWrap.setOnClickListener(v->{
            ix[0]=(ix[0]+1)%palettes.length;
            paint.run();
            GradientDrawable next=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                palettes[ix[0]]
            );
            next.setShape(GradientDrawable.OVAL);
            dot.setBackground(next);
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
        },160);
    }

'''
replace_method('    private void showMessageTextCreate(){',aa_method,'    private void pickInstagramMediaFullScreen(){','Aa page')

if s==orig:
    raise SystemExit("No source changes were made.")

p.write_text(s,encoding="utf-8")
print("Messenger v67 patch applied successfully.")
