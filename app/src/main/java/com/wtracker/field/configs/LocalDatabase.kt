package com.wtracker.field.configs

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wtracker.field.services.local.ClientDao
import com.wtracker.field.services.local.ClientEntity
import com.wtracker.field.services.local.EvidenceDao
import com.wtracker.field.services.local.EvidenceEntity
import com.wtracker.field.services.local.LocationPingDao
import com.wtracker.field.services.local.LocationPingEntity
import com.wtracker.field.services.local.PlannedVisitDao
import com.wtracker.field.services.local.PlannedVisitEntity
import com.wtracker.field.services.local.ShiftDao
import com.wtracker.field.services.local.ShiftEntity
import com.wtracker.field.services.local.StopEventDao
import com.wtracker.field.services.local.StopEventEntity
import com.wtracker.field.services.local.VisitRecordDao
import com.wtracker.field.services.local.VisitRecordEntity

@Database(
    entities = [
        ClientEntity::class,
        VisitRecordEntity::class,
        EvidenceEntity::class,
        ShiftEntity::class,
        LocationPingEntity::class,
        StopEventEntity::class,
        PlannedVisitEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun visitRecordDao(): VisitRecordDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun shiftDao(): ShiftDao
    abstract fun locationPingDao(): LocationPingDao
    abstract fun stopEventDao(): StopEventDao
    abstract fun plannedVisitDao(): PlannedVisitDao

    companion object {
        private const val DATABASE_NAME = "wtracker_field.db"

        fun build(context: Context): LocalDatabase =
            Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, DATABASE_NAME)
                .addMigrations(
                    LocalMigrations.MIGRATION_1_2,
                    LocalMigrations.MIGRATION_2_3,
                    LocalMigrations.MIGRATION_3_4,
                )
                .build()
    }
}

/**
 * Migraciones explícitas. Nunca usar `fallbackToDestructiveMigration`: borraría visitas y
 * evidencias que aún no se sincronizaron con el Backoffice.
 */
internal object LocalMigrations {

    /** v2: ruta del día (clientes elegidos por el trabajador, atados a la jornada). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `planned_visits` (" +
                    "`shiftId` TEXT NOT NULL, `clientId` TEXT NOT NULL, `addedAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`shiftId`, `clientId`))",
            )
        }
    }

    /** v3: el radio de geovalla deja de ser dato del cliente (ahora es un ajuste global). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `clients_new` (" +
                    "`id` TEXT NOT NULL, `businessName` TEXT NOT NULL, `contactName` TEXT, `phone` TEXT, " +
                    "`address` TEXT, `latitude` REAL, `longitude` REAL, `serverLastVisitStatus` TEXT, " +
                    "`serverLastVisitAtMillis` INTEGER, `downloadedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "INSERT INTO `clients_new` (id, businessName, contactName, phone, address, latitude, longitude, " +
                    "serverLastVisitStatus, serverLastVisitAtMillis, downloadedAtMillis) " +
                    "SELECT id, businessName, contactName, phone, address, latitude, longitude, " +
                    "serverLastVisitStatus, serverLastVisitAtMillis, downloadedAtMillis FROM `clients`",
            )
            db.execSQL("DROP TABLE `clients`")
            db.execSQL("ALTER TABLE `clients_new` RENAME TO `clients`")
        }
    }

    /** v4: clientCode, deliveryAddress y stallNumber en clientes para toma de pedidos. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `clients` ADD COLUMN `clientCode` TEXT")
            db.execSQL("ALTER TABLE `clients` ADD COLUMN `deliveryAddress` TEXT")
            db.execSQL("ALTER TABLE `clients` ADD COLUMN `stallNumber` TEXT")
        }
    }
}

/** Wrapper de transacciones: los servicios no dependen directamente de la API de Room. */
class LocalTransactionRunner(private val database: LocalDatabase) {
    suspend fun <R> runInTransaction(block: suspend () -> R): R = database.withTransaction(block)
}
