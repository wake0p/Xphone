# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.


# --- Enhanced Safety Rules for Release Crash Fix ---

# 1. Attributes & Annotations (Required for many libraries and debugging)
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Keep all App Code (Temporary fix to ensure stability while getting size benefits from libs)
-keep class com.safe.discipline.** { *; }

# 3. Hidden API Bypass (Crucial for Shizuku/Reflection)
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep interface org.lsposed.hiddenapibypass.** { *; }

# --- CRITICAL FIX: IPC/Binder/AIDL Support ---
# 必须保留所有 AIDL 生成的接口，否则跨进程回调（如 Shizuku 授权结果）会崩溃
-keep public interface * extends android.os.IInterface { *; }

# 必须保留 Parcelable 的 CREATOR 和读写方法，否则数据传递会崩溃
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
    public <init>(...);
    public int describeContents();
    public void writeToParcel(...);
}

# --- Shizuku Rules (Strict Mode) ---
# 强制保留所有类、接口、枚举及其所有成员，禁止任何重命名或移除
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep enum rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }
-keep enum moe.shizuku.** { *; }

# Preserve all native methods (JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class rikka.shizuku.Shizuku {
    java.lang.Process newProcess(...);
    public static *** *;
}

# --- Coroutines & Kotlin ---
-keep class kotlinx.coroutines.** { *; }

# --- Generic Safety ---
-keep class **.R$* { *; }