package com.cerclerouge.privacy.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.File
import java.security.SecureRandom

/**
 * Phrase de passe de la base SQLCipher (ADR-0007) : 32 octets aléatoires, chiffrés par un keyset Tink
 * lui-même enveloppé par une clé non exportable de l'Android Keystore. Jamais écrite en clair.
 * Exclue des sauvegardes (allowBackup=false) : perdre le téléphone, c'est perdre les données locales, voulu.
 */
object DatabaseKey {
    private const val FILE = "db.key.enc"
    private const val AAD = "cercle-rouge/db-passphrase/v1"

    fun getOrCreate(context: Context): ByteArray {
        val aead = aead(context)
        val file = File(context.noBackupFilesDir, FILE)
        if (file.exists()) return aead.decrypt(file.readBytes(), AAD.toByteArray())
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeBytes(aead.encrypt(passphrase, AAD.toByteArray()))
        check(tmp.renameTo(file)) { "écriture de la clé de base impossible" }
        return passphrase
    }

    private fun aead(context: Context): Aead {
        AeadConfig.register()
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, "cr_db_keyset", "cr_keysets")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://cr_db_master")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
}
