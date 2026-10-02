# 默认保留，必要时按需收紧
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.local.artifactboost.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.local.artifactboost.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
