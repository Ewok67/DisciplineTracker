#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "ai_agent"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Singleton inference state. A 1.5B Q4 model is ~1.1GB; keep it resident
// until the process dies and expose init/free/ask to the Kotlin layer.
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;

extern "C"
JNIEXPORT jint JNICALL
Java_com_ewok67_disciplinetracker_AgentEngine_nativeInit(JNIEnv *env, jobject /*thiz*/,
                                                         jstring jpath, jint nThreads) {
    if (g_ctx != nullptr) {
        return 1; // already loaded
    }
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (path == nullptr) return 0;

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU inference for maximum device compatibility

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (g_model == nullptr) {
        LOGE("failed to load model");
        return 0;
    }
    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params cparams = llama_context_default_params();
    int32_t n_train = llama_model_n_ctx_train(g_model);
    cparams.n_ctx = n_train > 0 && n_train < 3072 ? n_train : 3072;
    int threads = nThreads > 0 ? nThreads : 4;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
        LOGE("failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return 0;
    }
    return 1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_ewok67_disciplinetracker_AgentEngine_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_ewok67_disciplinetracker_AgentEngine_nativeAsk(JNIEnv *env, jobject /*thiz*/,
                                                        jstring jSystem, jstring jUser,
                                                        jint maxTokens) {
    if (g_ctx == nullptr) {
        return env->NewStringUTF("");
    }
    const char *sys = env->GetStringUTFChars(jSystem, nullptr);
    const char *usr = env->GetStringUTFChars(jUser, nullptr);
    if (sys == nullptr || usr == nullptr) {
        if (sys) env->ReleaseStringUTFChars(jSystem, sys);
        if (usr) env->ReleaseStringUTFChars(jUser, usr);
        return env->NewStringUTF("");
    }

    // 1. Apply the model's own chat template (handles Qwen/Gemma formats).
    llama_chat_message chat[2] = {{"system", sys}, {"user", usr}};
    int32_t need = llama_chat_apply_template(nullptr, chat, 2, true, nullptr, 0);
    if (need < 0) {
        LOGE("chat template sizing failed");
        env->ReleaseStringUTFChars(jSystem, sys);
        env->ReleaseStringUTFChars(jUser, usr);
        return env->NewStringUTF("");
    }
    std::vector<char> formatted(need + 1);
    int32_t used = llama_chat_apply_template(nullptr, chat, 2, true,
                                             formatted.data(), (int32_t)formatted.size());
    env->ReleaseStringUTFChars(jSystem, sys);
    env->ReleaseStringUTFChars(jUser, usr);
    if (used < 0) {
        return env->NewStringUTF("");
    }

    // 2. Tokenize.
    const char *text = formatted.data();
    int32_t n_prompt = -llama_tokenize(g_vocab, text, (int32_t)strlen(text),
                                       nullptr, 0, true, true);
    if (n_prompt <= 0) {
        return env->NewStringUTF("");
    }
    std::vector<llama_token> tokens(n_prompt);
    n_prompt = llama_tokenize(g_vocab, text, (int32_t)strlen(text),
                              tokens.data(), n_prompt, true, true);
    if (n_prompt < 0) {
        return env->NewStringUTF("");
    }

    // 3. Prefill the whole prompt, then greedy-decode up to maxTokens.
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt);
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("prefill decode failed");
        llama_sampler_free(smpl);
        return env->NewStringUTF("");
    }

    std::string out;
    for (int32_t i = 0; i < maxTokens; i++) {
        llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
        llama_sampler_accept(smpl, id);
        if (llama_vocab_is_eog(g_vocab, id)) {
            break;
        }
        char piece[256];
        int n = llama_token_to_piece(g_vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) {
            out.append(piece, n);
        }
        llama_batch next = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, next) != 0) {
            break;
        }
    }
    llama_sampler_free(smpl);

    return env->NewStringUTF(out.c_str());
}
