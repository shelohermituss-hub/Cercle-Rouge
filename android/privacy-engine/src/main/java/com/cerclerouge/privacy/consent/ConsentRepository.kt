package com.cerclerouge.privacy.consent

import com.cerclerouge.domain.Consent
import com.cerclerouge.domain.ConsentScope
import com.cerclerouge.privacy.db.ConsentDao
import com.cerclerouge.privacy.db.ConsentEntity
import java.time.Instant

/** Consentements historisés : chaque choix est une nouvelle ligne, jamais une modification (R-ONB-03). */
class ConsentRepository(private val dao: ConsentDao) {
    suspend fun record(consent: Consent) = dao.insert(
        ConsentEntity(
            scope = consent.scope.name,
            granted = consent.granted,
            textVersion = consent.textVersion,
            locale = consent.locale,
            atMillis = consent.at.toEpochMilli()
        )
    )

    suspend fun current(scope: ConsentScope): Consent? = dao.latest(scope.name)?.toDomain()

    suspend fun history(): List<Consent> = dao.history().map { it.toDomain() }

    private fun ConsentEntity.toDomain() =
        Consent(ConsentScope.valueOf(scope), granted, textVersion, locale, Instant.ofEpochMilli(atMillis))
}
