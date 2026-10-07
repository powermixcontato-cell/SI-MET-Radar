# Regras R8 do SI Met RADAR (release com isMinifyEnabled + isShrinkResources).
# Bibliotecas (Retrofit, OkHttp, Moshi codegen, Room, Coroutines, Play Services) já trazem regras próprias
# (consumer rules); aqui ficam só as garantias do código do app.

# Stack traces legíveis na Play Console (o mapping.txt vai dentro do AAB)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# Modelos JSON (Moshi com @JsonClass(generateAdapter = true)) e interfaces Retrofit
-keep class com.example.data.remote.** { *; }
-keep interface com.example.data.remote.** { *; }
-keep class **JsonAdapter { *; }

# Entidades e DAO do Room (o Room já gera código, mas mantém nomes estáveis para o schema exportado)
-keep class com.example.data.local.entity.** { *; }
-keep class com.example.data.local.dao.** { *; }

# Retrofit + suspend functions
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Widget e componentes declarados no manifest são mantidos pelo AAPT; nada extra aqui.

# Avisos de dependências opcionais do OkHttp
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
