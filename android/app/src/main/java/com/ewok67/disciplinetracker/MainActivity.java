package com.ewok67.disciplinetracker;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Thin native shell: hosts the single-file web app (index.html) in a WebView
 * and triggers the GitHub-release update check on launch.
 */
public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // localStorage persistence — required, the app stores all data there
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // keep navigation inside the app; no external browser handoff
        webView.setWebViewClient(new WebViewClient());

        setContentView(webView);

        // Bridge: lets the web app trigger a manual (unthrottled) update check
        // and read the installed version from settings.
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void checkForUpdate() {
                UpdateChecker.check(MainActivity.this, statusListener, true);
            }

            @JavascriptInterface
            public String getVersion() {
                return BuildConfig.VERSION_NAME;
            }

            @JavascriptInterface
            public String agentStatus() {
                return AgentEngine.status(MainActivity.this);
            }

            @JavascriptInterface
            public void agentDownload() {
                AgentEngine.downloadModel(MainActivity.this, new AgentEngine.Listener() {
                    @Override
                    public void onProgress(int percent) {
                        postToWeb("__onAgentProgress", String.valueOf(percent));
                    }

                    @Override
                    public void onDone() {
                        postToWeb("__onAgentStatus", "ready");
                    }

                    @Override
                    public void onError(String message) {
                        postToWeb("__onAgentStatus", "error");
                    }
                });
            }

            @JavascriptInterface
            public void agentAsk(String question, String dataJson) {
                AgentEngine.ask(MainActivity.this, question, dataJson, new AgentEngine.Callback() {
                    @Override
                    public void onResult(String text) {
                        postToWeb("__onAgentAnswer", text);
                    }

                    @Override
                    public void onError(String code) {
                        postToWeb("__onAgentError", code);
                    }
                });
            }
        }, "AndroidBridge");

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        } else {
            webView.restoreState(savedInstanceState);
        }

        // automatic, throttled check on launch
        UpdateChecker.check(this, statusListener, false);
    }

    /** Forwards update status (checking/latest/update-found/error) into the web UI. */
    private final UpdateChecker.Listener statusListener = new UpdateChecker.Listener() {
        @Override
        public void onStatus(final String status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (webView != null) {
                        webView.evaluateJavascript(
                                "window.__onUpdateStatus && window.__onUpdateStatus('"
                                        + status + "')", null);
                    }
                }
            });
        }
    };

    /** Posts a JS callback with one string payload (JSON-escaped). */
    private void postToWeb(final String fn, final String payload) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.evaluateJavascript(
                            "window." + fn + " && window." + fn + "("
                                    + toJsString(payload) + ")", null);
                }
            }
        });
    }

    private static String toJsString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
