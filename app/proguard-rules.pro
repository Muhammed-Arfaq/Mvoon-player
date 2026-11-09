# libVLC (org.videolan.libvlc) creates and calls these classes from native code via JNI, by name.
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**
