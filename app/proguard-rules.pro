# Keep JSch classes
-keep class com.jcraft.jsch.** { *; }
-keep class com.jcraft.jsch.jce.** { *; }
-keep class com.jcraft.jsch.jcraft.** { *; }
-keep class com.jcraft.jsch.jzlib.** { *; }

# Don't optimize JSch
-keep,allowobfuscation class com.jcraft.jsch.** { *; }

# Missing classes (not available on Android)
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep SmartBridge app classes
-keep class com.smartbridge.tunnel.** { *; }

# Keep reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
