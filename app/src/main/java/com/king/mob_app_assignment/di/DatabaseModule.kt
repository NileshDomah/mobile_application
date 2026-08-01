package com.king.mob_app_assignment.di

import android.content.Context
import androidx.room.Room
import com.king.mob_app_assignment.data.local.AppDatabase
import com.king.mob_app_assignment.data.local.PendingReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "shake_to_report.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun providePendingReportDao(appDatabase: AppDatabase): PendingReportDao =
        appDatabase.pendingReportDao()
}
