# Android default ProGuard rules
-keep class * extends java.util.ListResourceBundle {
    protected Object[][] getContents();
}

-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}

-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keepnames class * implements android.os.Parcelable {
    public static final *** CREATOR;
}

# Keep our app classes
-keep class com.securityshield.** { *; }

# Keep Hilt generated classes
-keep class **_Hilt_* { *; }
-keep class hilt_aggregated_deps.** { *; }

# Keep Retrofit interfaces
-keep interface com.securityshield.**.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Gemini AI
-keep class com.google.ai.client.generativeai.** { *; }

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }

# Suppress warnings
-dontwarn android.os.**
-dontwarn com.google.android.gms.**
