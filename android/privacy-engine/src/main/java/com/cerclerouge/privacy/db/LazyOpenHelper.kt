package com.cerclerouge.privacy.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * Retarde la création du vrai helper SQLCipher (chargement de la bibliothèque native, lecture de la clé dans
 * l'Android Keystore) jusqu'au premier accès à la base, qui a lieu sur un fil d'arrière-plan (requêtes `suspend`).
 * Sans cela, ce travail bloquerait le fil principal au démarrage (R-HOME-06 : accueil en moins de 500 ms).
 */
internal class LazyOpenHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val factory: () -> SupportSQLiteOpenHelper.Factory
) : SupportSQLiteOpenHelper {
    private var writeAheadLogging: Boolean? = null
    private val delegate by lazy {
        factory().create(configuration).also { helper -> writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled) }
    }

    override val databaseName: String? get() = configuration.name

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        writeAheadLogging = enabled
    }

    override val writableDatabase: SupportSQLiteDatabase get() = delegate.writableDatabase

    override val readableDatabase: SupportSQLiteDatabase get() = delegate.readableDatabase

    override fun close() = delegate.close()

    class Factory(private val factory: () -> SupportSQLiteOpenHelper.Factory) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
            LazyOpenHelper(configuration, factory)
    }
}
