package com.midnight.kuira.feature.balance.di

import android.os.Build
import androidx.annotation.RequiresApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Hilt module for balance feature dependencies.
 *
 * **Provides:**
 * - Clock for time-based operations (last updated timestamps, etc.)
 */
@Module
@InstallIn(SingletonComponent::class)
object BalanceModule {

    /**
     * Provides system default timezone Clock.
     *
     * **Why Clock is injected:**
     * - Testability: Can inject fake/fixed Clock in tests
     * - Time zone handling: Centralized time source
     * - Consistent time across ViewModels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Provides
    @Singleton
    fun provideClock(): Clock {
        return Clock.systemDefaultZone()
    }
}
