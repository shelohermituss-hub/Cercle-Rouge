package com.cerclerouge.app.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recherche dans le répertoire du téléphone (`READ_CONTACTS`, PDF 15.2), pour choisir un proche en tapant son nom. Lecture
 * locale à la demande : rien n'est copié ni envoyé, seul le contact choisi entre dans le carnet (R-CE-02). Sans la permission,
 * le sélecteur système (sans permission) reste disponible.
 */
@Singleton
class DirectorySearch @Inject constructor(@param:ApplicationContext private val context: Context) {
    val granted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Jusqu'à [MAX] numéros dont le nom contient `query` (au moins 2 lettres), sans doublon de numéro. */
    suspend fun search(query: String): List<PickedPhone> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (!granted || q.length < 2) return@withContext emptyList()
        val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        val rows = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            columns,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%${q.replace("%", "").replace("_", "")}%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < MAX * 3) add(PickedPhone(c.getString(0).orEmpty(), c.getString(1).orEmpty()))
            }
        }.orEmpty()
        rows.distinctBy { it.number.filter(Char::isDigit) }.take(MAX)
    }

    private companion object {
        const val MAX = 20
    }
}
