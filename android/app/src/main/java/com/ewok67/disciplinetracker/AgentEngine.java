package com.ewok67.disciplinetracker;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Local on-device assistant: loads a small GGUF model (Qwen2.5-1.5B-Instruct
 * Q4_K_M, ~1.1 GB, downloaded once) and answers questions about the user's
 * habit data. Inference runs via llama.cpp through JNI (see ai_agent.cpp).
 *
 * The app's data is a few KB of JSON, so no RAG/embeddings are needed — the
 * snapshot is injected straight into the system prompt.
 */
public class AgentEngine {

    public static final String MODEL_URL =
            "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/"
                    + "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf";

    private static final String PREFS = "agent_prefs";
    private static final String KEY_READY = "model_ready";

    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int MAX_ANSWER_TOKENS = 300;

    private static volatile boolean loaded = false;
    private static volatile int downloadPercent = -1; // -1 = not downloading

    static {
        System.loadLibrary("ai_agent");
    }

    private static native int nativeInit(String path, int nThreads);

    private static native void nativeFree();

    private static native String nativeAsk(String system, String user, int maxTokens);

    public interface Listener {
        void onProgress(int percent);

        void onDone();

        void onError(String message);
    }

    public interface Callback {
        void onResult(String text);

        void onError(String code);
    }

    public static File modelFile(Context context) {
        return new File(context.getFilesDir(), "model.gguf");
    }

    /** True when the model is downloaded and loaded into memory. */
    public static synchronized boolean isReady(Context context) {
        if (loaded) {
            return true;
        }
        File f = modelFile(context);
        if (f.exists() && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_READY, false)) {
            int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            loaded = nativeInit(f.getAbsolutePath(), threads) == 1;
        }
        return loaded;
    }

    /** "ready" | "downloading:N" | "missing" */
    public static synchronized String status(Context context) {
        if (loaded) {
            return "ready";
        }
        if (downloadPercent >= 0) {
            return "downloading:" + downloadPercent;
        }
        File f = modelFile(context);
        if (f.exists() && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_READY, false)) {
            return "loading";
        }
        return "missing";
    }

    /** Streams the ~1.1 GB model into app storage, reporting progress. */
    public static synchronized void downloadModel(final Context context, final Listener listener) {
        if (downloadPercent >= 0) {
            return; // already in progress
        }
        downloadPercent = 0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(MODEL_URL).openConnection();
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "DisciplineTracker-Android");
                    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        listener.onError("HTTP " + conn.getResponseCode());
                        downloadPercent = -1;
                        return;
                    }
                    long total = conn.getContentLengthLong();
                    File out = modelFile(context);
                    File tmp = new File(context.getFilesDir(), "model.gguf.part");
                    InputStream in = conn.getInputStream();
                    OutputStream fos = new FileOutputStream(tmp);
                    byte[] buffer = new byte[65536];
                    long read = 0;
                    int n;
                    int lastPercent = -1;
                    while ((n = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, n);
                        read += n;
                        if (total > 0) {
                            int percent = (int) (read * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                downloadPercent = percent;
                                listener.onProgress(percent);
                            }
                        }
                    }
                    fos.close();
                    in.close();
                    if (!tmp.renameTo(out)) {
                        listener.onError("could not save model");
                        downloadPercent = -1;
                        return;
                    }
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putBoolean(KEY_READY, true).apply();
                    downloadPercent = -1;
                    listener.onDone();
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError(e.getMessage() != null ? e.getMessage() : "download failed");
                    }
                    downloadPercent = -1;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }).start();
    }

    /** Answers one question about the user's data snapshot. Runs off the UI thread. */
    public static void ask(final Context context, final String question,
                           final String dataJson, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!isReady(context)) {
                    callback.onError("model-not-ready");
                    return;
                }
                try {
                    String system = buildSystemPrompt(dataJson);
                    String answer = nativeAsk(system, question, MAX_ANSWER_TOKENS);
                    if (answer == null || answer.trim().isEmpty()) {
                        callback.onError("empty");
                    } else {
                        callback.onResult(answer.trim());
                    }
                } catch (Exception e) {
                    callback.onError("inference-failed");
                }
            }
        }).start();
    }

    static String buildSystemPrompt(String dataJson) {
        return "You are the built-in assistant of the user's personal discipline "
                + "tracker app. Answer questions about their habits, tasks and "
                + "progress using ONLY the data below. If the data does not "
                + "contain the answer, say you don't have that information. "
                + "Be concise: 1-3 short sentences unless the user asks for "
                + "detail.\n"
                + "USER DATA (JSON, tasks and day-by-day completion where a "
                + "leading '!' marks a broken/stays-checked day):\n"
                + dataJson;
    }
}
