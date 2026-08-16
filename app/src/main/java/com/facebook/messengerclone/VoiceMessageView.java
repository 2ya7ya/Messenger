package com.facebook.messengerclone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VoiceMessageView extends LinearLayout {
    private static final int[] HEIGHTS = {5,5,5,5,6,7,8,11,15,22,34,39,36,8,7,13,37,29,22,15,8,13};
    private final ImageButton play;
    private final Wave wave;
    private final TextView time;
    private final String url;
    private final Map<String,String> headers;
    private MediaPlayer player;
    private int fallbackDurationMs;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable ticker = new Runnable() { @Override public void run() { sync(); if (player != null && player.isPlaying()) handler.postDelayed(this, 120); } };

    VoiceMessageView(Context context, String url, String fileName, Map<String,String> headers, int accent, int voiceBg, int waveColor, int textColor) {
        super(context);
        this.url = url;
        this.headers = headers;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(17), dp(13), dp(17), dp(13));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(voiceBg); bg.setCornerRadius(dp(27)); setBackground(bg);
        setMinimumHeight(dp(78));

        Matcher m = Pattern.compile("-([0-9]+)ms\\.[^.]+$", Pattern.CASE_INSENSITIVE).matcher(fileName == null ? "" : fileName);
        if (m.find()) try { fallbackDurationMs = Integer.parseInt(m.group(1)); } catch (Exception ignored) {}

        play = new ImageButton(context); play.setBackgroundColor(Color.TRANSPARENT); play.setPadding(0,0,0,0); play.setImageResource(R.drawable.ic_msg_play); play.setColorFilter(accent);
        LayoutParams pp = new LayoutParams(dp(31), dp(42)); pp.rightMargin = dp(11); addView(play, pp);

        wave = new Wave(context, accent, waveColor); addView(wave, new LayoutParams(0, dp(48), 1));

        time = new TextView(context); time.setTextColor(textColor); time.setTextSize(15); time.setGravity(Gravity.CENTER_VERTICAL | Gravity.END); time.setText(format(fallbackDurationMs / 1000.0));
        LayoutParams tp = new LayoutParams(dp(42), dp(48)); tp.leftMargin = dp(9); addView(time, tp);

        play.setOnClickListener(v -> toggle());
        wave.setOnSeekListener(f -> { if (player != null && player.getDuration() > 0) { player.seekTo((int)(player.getDuration() * f)); sync(); } });
    }

    private void toggle() {
        try {
            if (player == null) {
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build());
                player.setDataSource(getContext(), Uri.parse(url), headers);
                player.setOnPreparedListener(mp -> { if (fallbackDurationMs <= 0) fallbackDurationMs = mp.getDuration(); mp.start(); sync(); handler.post(ticker); });
                player.setOnCompletionListener(mp -> { mp.seekTo(0); sync(); });
                player.prepareAsync();
                return;
            }
            if (player.isPlaying()) player.pause(); else player.start();
            sync(); handler.removeCallbacks(ticker); handler.post(ticker);
        } catch (Exception ignored) {}
    }

    private void sync() {
        int dur = fallbackDurationMs, pos = 0; boolean playing = false;
        if (player != null) {
            try { if (player.getDuration() > 0) dur = player.getDuration(); pos = player.getCurrentPosition(); playing = player.isPlaying(); } catch (Exception ignored) {}
        }
        play.setImageResource(playing ? R.drawable.ic_msg_pause : R.drawable.ic_msg_play);
        wave.setProgress(dur > 0 ? Math.max(0f, Math.min(1f, pos / (float)dur)) : 0f);
        int shown = playing ? pos : (pos > 0 ? pos : dur);
        time.setText(format(shown / 1000.0));
    }

    void release() { handler.removeCallbacks(ticker); if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; } }

    private static String format(double seconds) { int s = Math.max(0, (int)Math.round(seconds)); return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private static final class Wave extends View {
        interface SeekListener { void seek(float fraction); }
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent, idle;
        private float progress;
        private SeekListener listener;
        Wave(Context c, int accent, int idle) { super(c); this.accent=accent; this.idle=idle; setClickable(true); }
        void setProgress(float p) { progress=p; invalidate(); }
        void setOnSeekListener(SeekListener l) { listener=l; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float density=getResources().getDisplayMetrics().density; float bar=4*density,gap=3*density,total=HEIGHTS.length*bar+(HEIGHTS.length-1)*gap,start=Math.max(0,(getWidth()-total)/2f),cy=getHeight()/2f;
            for(int i=0;i<HEIGHTS.length;i++){float h=HEIGHTS[i]*density;paint.setColor(((i+.5f)/HEIGHTS.length)<=progress?accent:idle);paint.setStrokeWidth(bar);paint.setStrokeCap(Paint.Cap.ROUND);float x=start+i*(bar+gap)+bar/2;c.drawLine(x,cy-h/2,x,cy+h/2,paint);} }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP){float f=Math.max(0,Math.min(1,e.getX()/Math.max(1f,getWidth())));progress=f;invalidate();if(listener!=null)listener.seek(f);return true;}return super.onTouchEvent(e);}    
    }
}
