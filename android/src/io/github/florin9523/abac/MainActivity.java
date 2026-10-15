package io.github.florin9523.abac;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Hosts the Abac web app (assets/www) in a full-screen WebView.
 *
 * The files are served from https://appassets.androidplatform.net, the host Android
 * reserves for bundled content, so the page gets a normal origin (localStorage keeps
 * history and settings) and the app never touches the network.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";

    // View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR, API 26
    private static final int FLAG_LIGHT_NAVIGATION_BAR = 0x00000010;

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int background = getColor(R.color.bg);
        drawBehindSystemBars(night);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(background);
        // Keep the page clear of the status and navigation bars; the root's colour shows behind them.
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });

        web = new WebView(this);
        web.setBackgroundColor(background);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setTextZoom(100); // the keypad layout is sized for 100%
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        web.addJavascriptInterface(new Bridge(), "AbacAndroid");
        web.setWebViewClient(new AssetClient());
        web.loadUrl(START_URL);
    }

    private void drawBehindSystemBars(boolean night) {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        // Before API 26 the navigation buttons are always white, so keep a dark bar under them in light mode.
        boolean darkButtonsAvailable = Build.VERSION.SDK_INT >= 26;
        window.setNavigationBarColor(night || darkButtonsAvailable ? Color.TRANSPARENT : 0xFF1B2430);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!night) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 29) {
            // Without this, 3-button navigation draws a translucent scrim over the page colour.
            try {
                Window.class.getMethod("setNavigationBarContrastEnforced", boolean.class).invoke(window, false);
            } catch (Exception ignored) {
                // Older framework without the method: the scrim stays, nothing else changes.
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Let the page close its history panel first.
        web.evaluateJavascript("window.abacBack ? window.abacBack() : false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String handled) {
                if (!"true".equals(handled)) {
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    /** Called from the page as window.AbacAndroid. Runs on a WebView background thread. */
    final class Bridge {
        @JavascriptInterface
        public void tap() {
            web.post(new Runnable() {
                @Override
                public void run() {
                    web.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            });
        }

        @JavascriptInterface
        public void copy(final String text) {
            web.post(new Runnable() {
                @Override
                public void run() {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Abac", text));
                    }
                }
            });
        }
    }

    /** Serves assets/www for the app host and refuses everything else. */
    final class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!HOST.equals(url.getHost())) {
                return error(403, "Forbidden");
            }
            String path = url.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                path = "/index.html";
            }
            if (path.contains("..")) {
                return error(404, "Not Found");
            }
            try {
                InputStream data = getAssets().open("www" + path);
                String mime = mimeType(path);
                String encoding = mime.startsWith("text/") || mime.endsWith("javascript") ? "utf-8" : null;
                return new WebResourceResponse(mime, encoding, data);
            } catch (IOException e) {
                return error(404, "Not Found");
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // The calculator has no links; never navigate away from it.
            return !HOST.equals(Uri.parse(url).getHost());
        }

        private WebResourceResponse error(int status, String reason) {
            return new WebResourceResponse("text/plain", "utf-8", status, reason,
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }
    }

    static String mimeType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".json") || path.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }
}
