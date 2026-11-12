package com.cerclerouge.privacy.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cerclerouge.privacy.crypto.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey val seq: Long,
    val utcMillis: Long,
    val type: String,
    val payload: String,
    val prevHash: String,
    val hash: String
)

@Entity(tableName = "consents")
data class ConsentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val granted: Boolean,
    val textVersion: String,
    val locale: String,
    val atMillis: Long
)

/** Réglages sensibles (PIN haché, conservation…) : clé/valeur dans la base chiffrée. */
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

/**
 * Contact de confiance (module produit 7). Chiffré avec le reste de la base ; jamais de copie du répertoire (R-CON-04).
 * `grp` plutôt que `group` : GROUP est un mot réservé de SQL.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val grp: String,
    val role: String,
    val language: String,
    val channels: String,
    val status: String,
    val position: Int,
    val isGuardian: Boolean,
    val hasApp: Boolean,
    val email: String? = null,
    val relation: String? = null
)

/** Envoi en attente (SMS, push…) : file persistante et idempotente (clé primaire = clé d'idempotence). */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val key: String,
    val alertId: String,
    val contactId: String,
    val channel: String,
    val kind: String,
    val priority: Int,
    val createdAtMillis: Long,
    val attempts: Int,
    val nextAttemptAtMillis: Long,
    val state: String
)

/**
 * Alerte enregistrée (Sprint 6). `wrappedKey` : keyset Tink `StreamingAead` de l'alerte, chiffré par une clé du Keystore.
 * Le mettre à null détruit les preuves (crypto-shredding, R-EE-02), même si un fichier traîne encore.
 */
@Entity(tableName = "evidence_alerts")
data class EvidenceAlertEntity(
    @PrimaryKey val alertId: String,
    val openedAtMillis: Long,
    val closedAtMillis: Long?,
    val lastWriteMillis: Long,
    val keptUntilMillis: Long?,
    val wrappedKey: ByteArray?
) {
    override fun equals(other: Any?): Boolean =
        other is EvidenceAlertEntity && other.alertId == alertId && other.lastWriteMillis == lastWriteMillis

    override fun hashCode(): Int = alertId.hashCode()
}

/** Segment chiffré (30 s) et son entrée dans la chaîne d'intégrité. `bytes` : taille du contenu en clair. */
@Entity(tableName = "evidence_segments", primaryKeys = ["alertId", "seq"])
data class EvidenceSegmentEntity(
    val alertId: String,
    val seq: Int,
    val startedAtMillis: Long,
    val durationMs: Long,
    val bytes: Long,
    val sha256: String,
    val prevHash: String,
    val entryHash: String
)

/**
 * Résumé d'une alerte ou d'un trajet terminé ou en cours (PDF 16.1) : heures, raison, niveau, issue. **Jamais de coordonnées.**
 * `kind` : « alert » ou « trip ». `reason` : motif de l'alerte (Reason) ou mode du trajet. `outcome` : safe, ended, arrived, stopped.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val reason: String?,
    val level: Int?,
    val outcome: String?
)

@Dao
interface HistoryDao {
    @Upsert suspend fun put(e: HistoryEntity)

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: String): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY startedAtMillis DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE kind = :kind ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun last(kind: String): HistoryEntity?

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence_alerts ORDER BY openedAtMillis DESC")
    suspend fun alerts(): List<EvidenceAlertEntity>

    @Query("SELECT * FROM evidence_alerts WHERE alertId = :id")
    suspend fun alert(id: String): EvidenceAlertEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(a: EvidenceAlertEntity): Long

    @Query("UPDATE evidence_alerts SET closedAtMillis = :at WHERE alertId = :id AND closedAtMillis IS NULL")
    suspend fun close(id: String, at: Long)

    @Query("UPDATE evidence_alerts SET keptUntilMillis = :until WHERE alertId = :id")
    suspend fun keep(id: String, until: Long)

    @Query("UPDATE evidence_alerts SET wrappedKey = NULL WHERE alertId = :id")
    suspend fun shredKey(id: String)

    @Query("SELECT * FROM evidence_segments WHERE alertId = :id ORDER BY seq")
    suspend fun segments(id: String): List<EvidenceSegmentEntity>

    @Insert suspend fun insertSegment(s: EvidenceSegmentEntity)

    @Query("UPDATE evidence_alerts SET lastWriteMillis = :at WHERE alertId = :id")
    suspend fun touch(id: String, at: Long)

    /** Ajoute le segment et met à jour la date de dernière écriture, d'un bloc. */
    @Transaction
    suspend fun addSegment(s: EvidenceSegmentEntity, at: Long) {
        insertSegment(s)
        touch(s.alertId, at)
    }

    @Query("DELETE FROM evidence_segments WHERE alertId = :id")
    suspend fun deleteSegments(id: String)

    @Query("DELETE FROM evidence_alerts WHERE alertId = :id")
    suspend fun deleteAlert(id: String)

    @Query("UPDATE evidence_alerts SET wrappedKey = NULL")
    suspend fun shredAll()
}

@Dao
interface EventLogDao {
    @Insert suspend fun insert(entry: EventLogEntity)

    @Query("SELECT * FROM event_log ORDER BY seq DESC LIMIT 1")
    suspend fun last(): EventLogEntity?

    @Query("SELECT * FROM event_log ORDER BY seq")
    suspend fun all(): List<EventLogEntity>

    @Query("SELECT * FROM event_log ORDER BY seq DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<EventLogEntity>

    @Query("DELETE FROM event_log WHERE utcMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int
}

@Dao
interface ConsentDao {
    @Insert suspend fun insert(consent: ConsentEntity)

    @Query("SELECT * FROM consents WHERE scope = :scope ORDER BY atMillis DESC, id DESC LIMIT 1")
    suspend fun latest(scope: String): ConsentEntity?

    @Query("SELECT * FROM consents ORDER BY atMillis, id")
    suspend fun history(): List<ConsentEntity>
}

@Dao
interface SettingDao {
    @Upsert suspend fun put(setting: SettingEntity)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY position")
    suspend fun all(): List<ContactEntity>

    @Insert suspend fun insertAll(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun clear()

    /** Remplace tout le carnet d'un coup : l'ordre et les statuts restent cohérents même si le processus meurt en route. */
    @Transaction
    suspend fun replaceAll(contacts: List<ContactEntity>) {
        clear()
        insertAll(contacts)
    }
}

@Dao
interface OutboxDao {
    /** Sans effet si la clé existe déjà : un même envoi mis deux fois dans la file n'existe qu'une fois. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(job: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY createdAtMillis, `key`")
    suspend fun all(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE `key` = :key")
    suspend fun get(key: String): OutboxEntity?

    @Query("UPDATE outbox SET attempts = :attempts, nextAttemptAtMillis = :next, state = :state WHERE `key` = :key")
    suspend fun update(key: String, attempts: Int, next: Long, state: String)

    @Query("DELETE FROM outbox WHERE state != 'PENDING' AND createdAtMillis < :beforeMillis")
    suspend fun purgeFinished(beforeMillis: Long): Int
}

@Database(
    entities = [
        EventLogEntity::class, ConsentEntity::class, SettingEntity::class, ContactEntity::class, OutboxEntity::class,
        EvidenceAlertEntity::class, EvidenceSegmentEntity::class, HistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class PrivacyDatabase : RoomDatabase() {
    abstract fun eventLog(): EventLogDao

    abstract fun consents(): ConsentDao

    abstract fun settings(): SettingDao

    abstract fun contacts(): ContactDao

    abstract fun outbox(): OutboxDao

    abstract fun evidence(): EvidenceDao

    abstract fun history(): HistoryDao

    companion object {
        const val NAME = "cercle-rouge.db"

        /** v5 → v6 : historique des alertes et des trajets (PDF 16.1), sans coordonnées. Tout le reste est conservé. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `startedAtMillis` INTEGER NOT NULL, " +
                        "`endedAtMillis` INTEGER, `reason` TEXT, `level` INTEGER, `outcome` TEXT, PRIMARY KEY(`id`))"
                )
            }
        }

        /** v4 → v5 : e-mail et relation des contacts de confiance (PDF 12.1). Tout le reste est conservé. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contacts` ADD COLUMN `email` TEXT")
                db.execSQL("ALTER TABLE `contacts` ADD COLUMN `relation` TEXT")
            }
        }

        /** v3 → v4 : preuves chiffrées (Sprint 6). Tout le reste est conservé. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `evidence_alerts` (`alertId` TEXT NOT NULL, `openedAtMillis` INTEGER NOT NULL, " +
                        "`closedAtMillis` INTEGER, `lastWriteMillis` INTEGER NOT NULL, `keptUntilMillis` INTEGER, `wrappedKey` BLOB, " +
                        "PRIMARY KEY(`alertId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `evidence_segments` (`alertId` TEXT NOT NULL, `seq` INTEGER NOT NULL, " +
                        "`startedAtMillis` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `bytes` INTEGER NOT NULL, " +
                        "`sha256` TEXT NOT NULL, `prevHash` TEXT NOT NULL, `entryHash` TEXT NOT NULL, PRIMARY KEY(`alertId`, `seq`))"
                )
            }
        }

        /** v2 → v3 : file d'attente des envois (Sprint 2). Tout le reste est conservé. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox` (`key` TEXT NOT NULL, `alertId` TEXT NOT NULL, `contactId` TEXT NOT NULL, " +
                        "`channel` TEXT NOT NULL, `kind` TEXT NOT NULL, `priority` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, `nextAttemptAtMillis` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`key`))"
                )
            }
        }

        /** v1 → v2 : table des contacts de confiance (Sprint 2). Le journal, les consentements et le PIN sont conservés. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contacts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "`grp` TEXT NOT NULL, `role` TEXT NOT NULL, `language` TEXT NOT NULL, `channels` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `position` INTEGER NOT NULL, `isGuardian` INTEGER NOT NULL, " +
                        "`hasApp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * Base Room chiffrée par SQLCipher (AES-256), clé issue de [DatabaseKey].
         * La clé est déjà aléatoire sur 256 bits : on la passe en clé brute (« x'…' ») pour éviter la dérivation
         * PBKDF2 de SQLCipher à chaque ouverture, inutile ici et coûteuse sur les téléphones d'entrée de gamme.
         */
        fun open(context: Context): PrivacyDatabase {
            val factory = LazyOpenHelper.Factory {
                System.loadLibrary("sqlcipher")
                val key = DatabaseKey.getOrCreate(context)
                val rawKey = "x'" + key.joinToString("") { "%02x".format(it) } + "'"
                key.fill(0)
                SupportOpenHelperFactory(rawKey.toByteArray(Charsets.US_ASCII))
            }
            return Room.databaseBuilder(context, PrivacyDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }
    }
}
