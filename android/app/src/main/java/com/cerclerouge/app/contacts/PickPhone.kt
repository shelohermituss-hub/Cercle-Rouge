package com.cerclerouge.app.contacts

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract

data class PickedPhone(val name: String, val number: String)

/**
 * Sélecteur de contact du système : aucune permission `READ_CONTACTS` (R-CON-04, produit 7). Android n'accorde à l'application
 * que la lecture du seul contact choisi. Rien d'autre du répertoire n'est lu ni copié.
 */
class PickPhone(private val context: Context) : ActivityResultContract<Unit, PickedPhone?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): PickedPhone? {
        val uri = intent?.data ?: return null
        val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        return context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
            if (c.moveToFirst()) PickedPhone(c.getString(0).orEmpty(), c.getString(1).orEmpty()) else null
        }
    }
}
