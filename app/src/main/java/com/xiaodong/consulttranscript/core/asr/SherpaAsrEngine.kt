package com.xiaodong.consulttranscript.core.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import java.io.File

/**
 * sherpa-onnx 真实识别引擎（端侧、完全离线）。
 *
 * 已用随包 AAR（sherpa-onnx-1.13.8.aar）的 classes.jar 经 javap 逐一核对类名与签名：
 *   OfflineRecognizer(assetManager: AssetManager?, config) —— 第一个参数传 null 时
 *     底层走 newFromFile(config)，即读「绝对文件路径」；正是 AssetCopier 把 assets
 *     模型拷进 filesDir 后的加载方式。（字节码 ifnull→newFromFile 已核实）
 *   createStream() / decode(stream) / getResult(stream).text
 *   OfflineStream.acceptWaveform(samples: FloatArray, sampleRate: Int) / release()
 *
 * 说明：这些 Config 是 Kotlin data class、全字段带默认值，但 class 未保留 Java 参数名，
 * 为避免「命名参数拼错」导致编译失败，统一用「无参构造 + setter 赋值」，
 * 只把必要字段设进去，其余走库默认值。
 *
 * 模型：SenseVoice-Small 中英日韩粤 int8 量化版（model.int8.onnx + tokens.txt）。
 * ★ 全程离线：只有「读本地文件 + 本地推理」，没有任何网络调用。
 */
class SherpaAsrEngine(private val context: Context) : AsrEngine {

    private var recognizer: OfflineRecognizer? = null

    override fun load(modelDir: File) {
        // assets 里的二进制模型必须先拷到 filesDir，native 层才能按绝对路径打开
        val asrDir = AssetCopier.copyAssets(context, "models/asr")
        val modelPath = File(asrDir, "model.int8.onnx").path
        val tokensPath = File(asrDir, "tokens.txt").path

        // SenseVoice 分支：中文 + 打开逆文本规整（数字/标点规整）
        val senseVoice = OfflineSenseVoiceModelConfig()
        senseVoice.model = modelPath
        senseVoice.language = "zh"
        senseVoice.useInverseTextNormalization = true

        val modelConfig = OfflineModelConfig()
        modelConfig.senseVoice = senseVoice
        modelConfig.tokens = tokensPath
        // 手机别吃满核心，长时转写会烫、会耗电
        modelConfig.numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        modelConfig.debug = false

        val config = OfflineRecognizerConfig()
        config.modelConfig = modelConfig

        // assetManager 传 null → newFromFile → 用上面的绝对路径加载模型
        recognizer = OfflineRecognizer(null, config)
    }

    override fun transcribe(samples: FloatArray): String {
        val rec = recognizer ?: error("引擎还没 load")
        var stream: OfflineStream? = null
        return try {
            stream = rec.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            rec.decode(stream)
            rec.getResult(stream).text
        } finally {
            @Suppress("UNNECESSARY_SAFE_CALL")
            stream?.release()
        }
    }

    override fun close() {
        recognizer?.release()
        recognizer = null
    }

    private companion object {
        const val SAMPLE_RATE = 16000
    }
}
