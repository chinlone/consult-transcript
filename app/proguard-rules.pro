# 混淆时保留 Room 实体与 sherpa-onnx 的 native 方法名，否则转写会崩
-keep class com.xiaodong.consulttranscript.data.db.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.k2fsa.sherpa.onnx.**
