package com.cerclerouge.app.alert

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cerclerouge.app.contacts.OutboxRepository
import com.cerclerouge.app.protection.ProtectionController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * File hors-ligne fiable (S2.06, R-NET-01, R-SY-01) : si des envois restent dans la file, une tâche WorkManager les reprend dès
 * que le réseau revient, même si le processus a été tué entre-temps ou si la protection a été coupée. Les envois sont idempotents
 * (clé par alerte, proche, canal et type) : la tâche ne crée jamais de doublon.
 */
object OutboxWork {
    private const val NAME = "cr-outbox"

    /** Programme une reprise au retour du réseau (une seule à la fois). */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OutboxEntryPoint {
    fun dispatcher(): AlertDispatcher
    fun outbox(): OutboxRepository
    fun controller(): ProtectionController
}

class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, OutboxEntryPoint::class.java)
        if (!deps.outbox().hasPending()) return Result.success()
        deps.controller().ensureRestored()
        deps.dispatcher().onNetworkBack(deps.controller().state.value.alert)
        return Result.success()
    }
}
