package com.xiaodong.consulttranscript.core.text

/**
 * 标点恢复。逐字稿没标点几乎没法读。
 * V1 用「轻量规则」先顶上：按段尾补句号、长段按停顿补逗号，不额外打包模型、不增加包体。
 * 若接了 sherpa-onnx 自带的标点模型，把 restore() 换成模型调用即可，其他文件不动。
 */
object Punctuation {
    /**
     * 这一段识别结果是否含「实际文字」。
     * SenseVoice 对静音、咳嗽、环境噪声常吐出纯标点（如「。」「，」），
     * 这类段没有任何可读内容，必须过滤，否则逐字稿里全是孤零零的句号。
     */
    fun isMeaningful(raw: String): Boolean {
        if (raw.isBlank()) return false
        // 只保留字母/数字/汉字（isLetterOrDigit 对 CJK 汉字同样为 true），
        // 过滤后为空说明这段全是标点或空白 → 无意义
        return raw.any { it.isLetterOrDigit() }
    }

    fun restore(raw: String, chunkDurationMs: Long): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        var s = t
        // 长段中间按空格/长间隔补逗号（中文模型一般不带空格，这里按长度保守处理）
        if (chunkDurationMs > 8_000L && s.length > 28 && !s.contains('，')) {
            val cut = s.length / 2
            s = s.substring(0, cut) + "，" + s.substring(cut)
        }
        // 句末标点
        if (s.last() !in "。！？，、；：,.!?") {
            s += if (s.last() in "吗呢吧啊呀么") "？" else "。"
        }
        return s
    }
}
