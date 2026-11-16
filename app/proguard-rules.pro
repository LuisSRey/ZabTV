# Reglas R8 de ZabTV (Task 16).
# Media3, Room, WorkManager, Coil y Paging traen sus propias consumer rules;
# aquí solo los keeps específicos de la app.

# EpgWorker se instancia por nombre vía WorkerFactory por defecto.
-keep class com.zabtv.app.data.work.EpgWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# CleartextGate: nombre estable para auditoría (acceptance.sh verifica su presencia).
-keepnames class com.zabtv.app.data.net.CleartextGate

# Modelos usados por reflexión en tests/logs (no ofuscar su forma).
-keepnames class com.zabtv.app.data.db.** { *; }
