# Regras R8/ProGuard do release. kotlinx.serialization + Retrofit + Hilt em geral
# funcionam sem regras extras nas versoes atuais; adicionar aqui conforme surgir
# warning no build de release (assembleRelease).

# kotlinx.serialization: mantem os @Serializable
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class app.astra.mobile.**$$serializer { *; }
-keepclassmembers class app.astra.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class app.astra.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
