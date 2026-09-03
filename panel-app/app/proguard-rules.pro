# ============================================================================
# App-Panel 混淆与资源精简规则
#
# 适用范围：release 构建（isMinifyEnabled = true + isShrinkResources = true）
#
# 关键前提：本项目网络层使用 Retrofit + Gson 转换器，data/remote/api 与
# data/model 下的 data class 均为 JSON 映射模型，且未使用 @SerializedName 注解。
# 一旦字段被 R8 重命名，反序列化将整体失效，故相关包必须完整保留。
#
# R8 全模式（full mode）自 AGP 8.0 起默认启用，无需额外声明。
# ============================================================================

# ----------------------------------------------------------------------------
# 一、通用属性
# ----------------------------------------------------------------------------

# 保留泛型签名：Retrofit 依赖反射解析接口方法的泛型返回类型
-keepattributes Signature

# 保留注解：Room / Dagger / Retrofit 均在运行时读取注解
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault

# 保留内部类与外围方法信息：避免 Gson 反序列化嵌套模型时结构丢失
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 保留源码行号：便于线上崩溃堆栈定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ----------------------------------------------------------------------------
# 二、Gson 序列化和反序列化模型（本项目最关键的一组规则）
# ----------------------------------------------------------------------------

# API 请求/响应模型：QlCronItem、BaihuTaskItem、GitHubRelease 等均在包内
-keep class com.panel.app.data.remote.api.** { *; }

# 统一业务模型：UnifiedTask、UnifiedEnv、UnifiedSubscription、TaskInstanceRecord 等
-keep class com.panel.app.data.model.** { *; }

# 支持将来接入 @SerializedName：保留被该注解标记的字段
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson 扩展点与泛型令牌（杜绝 R8 擦除泛型实参签名）
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    <init>(...);
    *;
}
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ----------------------------------------------------------------------------
# 三、Retrofit / OkHttp
# ----------------------------------------------------------------------------

# 保留含 Retrofit 注解的接口方法，R8 需要据此解析请求定义
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# OkHttp 与 okio 自带 consumer 规则，此处仅消除平台类缺失告警
-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ----------------------------------------------------------------------------
# 四、Room 持久化
# ----------------------------------------------------------------------------

# 数据库、DAO 与类型转换器（PanelInstance 实体已由 data.model 规则覆盖）
-keep class com.panel.app.data.local.db.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ----------------------------------------------------------------------------
# 五、Hilt / Dagger 依赖注入
# ----------------------------------------------------------------------------

# 保留被注入的构造函数（如 PanelRepository、MainViewModel）
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* *;
    @dagger.* *;
    @dagger.hilt.* *;
}

# Application 类由 @HiltAndroidApp 标记，作为注入根需保留
-keep class com.panel.app.PanelApp { *; }
