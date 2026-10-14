# Retrofit, OkHttp, kotlinx.serialization y Room incluyen sus propias reglas de consumidor.
# Mantener los DTOs serializables por seguridad cuando se habilite minify.
-keep class com.wtracker.field.services.api.dto.** { *; }
