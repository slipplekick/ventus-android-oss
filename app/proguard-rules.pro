# R8 shrinking + obfuscation rules for the release build. Each block below
# exists because that library needs reflection (or a stable class/member
# name) at runtime that R8's default shrinking would otherwise break.

# ---- kotlinx.serialization -------------------------------------------
# Official rules from kotlinx.serialization's own docs: the generated
# `$$serializer` companion lookup is reflective, so both the serializer
# classes and the annotations describing them must survive.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ventus.sys.data.remote.dto.**$$serializer { *; }
-keepclassmembers class com.ventus.sys.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.ventus.sys.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class com.ventus.sys.data.remote.dto.** { *; }

# ---- Retrofit / OkHttp -------------------------------------------------
# Retrofit inspects @GET/@POST/etc. annotations reflectively to build each
# service at runtime, and needs generic signatures to resolve response
# types (List<T>, Response<T>). Both are R8-obfuscation casualties by
# default; the API interfaces themselves are also kept outright since
# they're implemented via a dynamic proxy, not compiled call sites.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.ventus.sys.data.remote.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Room ---------------------------------------------------------------
# Room's KSP-generated *_Impl classes call entity constructors/fields
# directly (compiled references, not reflection), so no rules are needed
# for the entities themselves. Room's own AAR ships the handful of rules
# its generated code does need.

# ---- AppAuth-Android (PKCE OAuth) --------------------------------------
# Keeps AuthState's JSON round-trip (TokenStore persists it as a
# serialized blob) and the library's internal model classes intact —
# auth is the one place a silent obfuscation-related crash is
# unacceptable, so this is a deliberately broad, low-risk keep for a
# small library rather than a narrowly-scoped one.
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# ---- AndroidX Security Crypto (EncryptedSharedPreferences) -------------
# Tink (the crypto library security-crypto wraps) resolves its key-manager
# implementations via a runtime registry lookup - obfuscating or
# stripping any of it causes a GeneralSecurityException at first use, not
# a build-time error, so this is a well-known required exception rather
# than a defensive guess.
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**
# Tink's KeysDownloader (a remote-key-fetch feature security-crypto never
# exercises) references an optional google-http-client + joda-time dependency
# neither of which this app pulls in - safe to silence, not a real gap.
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**

# ---- Coil -----------------------------------------------------------------
# Coil ships its own consumer ProGuard rules; nothing extra needed here.

# ---- Hilt / Dagger --------------------------------------------------------
# Generated bindings are compiled call sites, not reflection; Dagger/Hilt's
# AAR ships the rules its own generated code needs.

# ---- General Kotlin metadata ---------------------------------------------
# Keeps data class componentN()/copy() reflection-friendly (used by some
# equals/hashCode/toString-generating library internals) without disabling
# obfuscation for the whole app.
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keep class kotlin.coroutines.Continuation
