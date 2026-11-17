package com.xiaodong.consulttranscript.core.asr

import java.io.File

/** 语音识别引擎的接口。全部第三方库调用都藏在实现里，业务层只认这个接口。 */
interface AsrEngine {
    /** 从 assets 里把模型拷到私有目录后加载（assets 里的压缩文件不能直接给 native 读） */
    fun load(modelDir: File)
    /** 把一小段 16k 单声道 PCM（float，-1..1）转成文字 */
    fun transcribe(samples: FloatArray): String
    fun close()
}

/** 按静音切段的接口 */
interface VadSegmenter {
    /** 输入整段 PCM，返回一串 (起始毫秒, 结束毫秒, 该段采样) */
    fun split(samples: FloatArray): List<AudioChunk>
}

data class AudioChunk(val startMs: Long, val endMs: Long, val samples: FloatArray)
