# LSPosed 入口类不能被混淆/裁剪
-keep class com.LT.SegNBPicHooker.hook.** { *; }
-keep class com.LT.SegNBPicHooker.provider.** { *; }
-keepclassmembers class * extends android.content.ContentProvider { *; }
-dontwarn de.robv.android.xposed.**
