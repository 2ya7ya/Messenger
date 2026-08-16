package com.facebook.messengerclone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 7001;
    private static final int WEB_PERMISSION_REQUEST = 7002;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;
    private final String baseUrl = BuildConfig.BASE_URL.replaceAll("/$", "");
    private String allowedHost;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { allowedHost = URI.create(baseUrl).getHost(); } catch (Exception ignored) { allowedHost = ""; }

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, 5);
        root.addView(progress, p);
        setContentView(root);

        configureWebView();
        if (state != null) webView.restoreState(state); else webView.loadUrl(baseUrl + "/");
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setBackgroundColor(Color.WHITE);
        webView.addJavascriptInterface(new AndroidBridge(), "MessengerAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host == null || host.equalsIgnoreCase(allowedHost)) {
                    if (uri.toString().startsWith(baseUrl + "/app")) {
                        view.setVisibility(View.INVISIBLE);
                    }
                    return false;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progress.setVisibility(View.GONE);
                if (url.startsWith(baseUrl + "/app")) {
    enterMessengerOnlyMode();
    webView.evaluateJavascript(
        "setTimeout(function(){" +
        "var p=document.getElementById('facebookMessengerPage');" +
        "if(p){p.classList.add('is-open');}" +
        "var chats=document.querySelector('[data-msg-tab=\\\"chats\\\"]');" +
        "if(chats){chats.click();}" +
        "},300);",
        null
    );
}
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                try { startActivityForResult(intent, FILE_CHOOSER_REQUEST); }
                catch (ActivityNotFoundException e) {
                    Intent fallback = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(fallback, "Choose media"), FILE_CHOOSER_REQUEST);
                }
                return true;
            }

            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermission(request));
            }
        });
    }

    private void enterMessengerOnlyMode() {
        String js = "(function(){" +
                "function boot(){" +
                "if(!window.__facebookOpenMessenger){setTimeout(boot,50);return;}" +
                "window.__facebookOpenMessenger();" +
                "setTimeout(function(){" +
                "var p=document.getElementById('facebookMessengerPage');" +
                "if(!p){setTimeout(boot,50);return;}" +
                "p.classList.add('is-open');" +
                "var chats=p.querySelector('[data-msg-tab=\\\"chats\\\"]');" +
                "if(chats){chats.click();}" +
                "var close=p.querySelector('[data-msg-close]');" +
                "if(close&&!close.dataset.androidBound){" +
                "close.dataset.androidBound='1';" +
                "close.addEventListener('click',function(e){" +
                "e.preventDefault();e.stopImmediatePropagation();" +
                "MessengerAndroid.closeApp();" +
                "},true);" +
                "}" +
                "MessengerAndroid.showMessenger();" +
                "},100);" +
                "}" +
                "boot();" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void handleWebPermission(PermissionRequest request) {
        if (request.getOrigin() == null || !allowedHost.equalsIgnoreCase(request.getOrigin().getHost())) {
            request.deny(); return;
        }
        List<String> needed = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.RECORD_AUDIO);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.CAMERA);
        }
        if (needed.isEmpty()) request.grant(request.getResources());
        else {
            pendingWebPermission = request;
            requestPermissions(needed.toArray(new String[0]), WEB_PERMISSION_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != WEB_PERMISSION_REQUEST || pendingWebPermission == null) return;
        boolean granted = true;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
        if (granted) pendingWebPermission.grant(pendingWebPermission.getResources()); else pendingWebPermission.deny();
        pendingWebPermission = null;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int i=0;i<n;i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) result = new Uri[]{ data.getData() };
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override public void onBackPressed() {
        webView.evaluateJavascript("(function(){var p=document.getElementById('facebookMessengerPage');if(!p||!p.classList.contains('is-open'))return 'exit';var chat=p.querySelector('.fb-msg-chat.is-active');var contacts=p.querySelector('.fb-msg-contact-head.is-active');if(chat){p.querySelector('[data-msg-chat-back]')?.click();return 'handled';}if(contacts){p.querySelector('[data-msg-contact-back]')?.click();return 'handled';}return 'exit';})()", value -> {
            if (value == null || value.contains("exit")) finish();
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        webView.saveState(out); super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.removeJavascriptInterface("MessengerAndroid"); webView.destroy(); }
        super.onDestroy();
    }

    private class AndroidBridge {
        @JavascriptInterface public void closeApp() {
            runOnUiThread(MainActivity.this::finish);
        }

        @JavascriptInterface public void showMessenger() {
            runOnUiThread(() -> webView.setVisibility(View.VISIBLE));
        }
    }
}
