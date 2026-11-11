# Pleos Vehicle SDK: keep it as shipped (the SDK binds car services by name; we do not inspect it).
-keep class ai.pleos.** { *; }
-dontwarn ai.pleos.**
# The SDK parses capability results with Gson TypeToken. R8 strips generic signatures by default and
# every capability check then fails ("TypeToken must be created with a type argument"), so the app
# thinks the car has no lights/windows/fan and moves nothing. Keep signatures and Gson as shipped.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**
# android.car is provided by the head unit (useLibrary "android.car").
-dontwarn android.car.**

# Haechi mascot: JavaScript calls these WebView bridge methods by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Keep log tags readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
