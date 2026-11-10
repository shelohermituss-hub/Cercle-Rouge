package com.cerclerouge.privacy.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
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

@Database(entities = [EventLogEntity::class, ConsentEntity::class, SettingEntity::class], version = 1, exportSchema = false)
abstract class PrivacyDatabase : RoomDatabase() {
    abstract fun eventLog(): EventLogDao

    abstract fun consents(): ConsentDao

    abstract fun settings(): SettingDao

    companion object {
        const val NAME = "cercle-rouge.db"

        /**
         * Base Room chiffrée par SQLCipher (AES-256), clé issue de [DatabaseKey].
         * La clé est déjà aléatoire sur 256 bits : on la passe en clé brute (« x'…' ») pour éviter la dérivation
         * PBKDF2 de SQLCipher à chaque ouverture, inutile ici et coûteuse sur les téléphones d'entrée de gamme.
         */
        fun open(context: Context): PrivacyDatabase {
            System.loadLibrary("sqlcipher")
            val key = DatabaseKey.getOrCreate(context)
            val rawKey = "x'" + key.joinToString("") { "%02x".format(it) } + "'"
            key.fill(0)
            val factory = SupportOpenHelperFactory(rawKey.toByteArray(Charsets.US_ASCII))
            return Room.databaseBuilder(context, PrivacyDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .build()
        }
    }
}
