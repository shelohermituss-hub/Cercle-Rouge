package com.cerclerouge.app.di

import android.content.Context
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.consent.ConsentRepository
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.log.EventLog
import com.cerclerouge.privacy.pin.PinRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): PrivacyDatabase = PrivacyDatabase.open(context)

    @Provides @Singleton
    fun eventLog(db: PrivacyDatabase, clock: Clock) = EventLog(db.eventLog(), clock)

    @Provides @Singleton
    fun pins(db: PrivacyDatabase, clock: Clock) = PinRepository(db.settings(), clock)

    @Provides @Singleton
    fun consents(db: PrivacyDatabase) = ConsentRepository(db.consents())

    @Provides @Singleton
    fun settings(db: PrivacyDatabase) = Settings(db.settings())
}
