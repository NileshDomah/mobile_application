package com.king.mob_app_assignment.di

import com.king.mob_app_assignment.data.repository.AuditLogRepository
import com.king.mob_app_assignment.data.repository.AuditLogRepositoryImpl
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.AuthRepositoryImpl
import com.king.mob_app_assignment.data.repository.IncidentRepository
import com.king.mob_app_assignment.data.repository.IncidentRepositoryImpl
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.data.repository.ReportRepositoryImpl
import com.king.mob_app_assignment.data.repository.RiskScoreRepository
import com.king.mob_app_assignment.data.repository.RiskScoreRepositoryImpl
import com.king.mob_app_assignment.data.repository.SupportRepository
import com.king.mob_app_assignment.data.repository.SupportRepositoryImpl
import com.king.mob_app_assignment.data.repository.WorkOrderRepository
import com.king.mob_app_assignment.data.repository.WorkOrderRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds
    @Singleton
    abstract fun bindSupportRepository(impl: SupportRepositoryImpl): SupportRepository

    @Binds
    @Singleton
    abstract fun bindIncidentRepository(impl: IncidentRepositoryImpl): IncidentRepository

    @Binds
    @Singleton
    abstract fun bindAuditLogRepository(impl: AuditLogRepositoryImpl): AuditLogRepository

    @Binds
    @Singleton
    abstract fun bindWorkOrderRepository(impl: WorkOrderRepositoryImpl): WorkOrderRepository

    @Binds
    @Singleton
    abstract fun bindRiskScoreRepository(impl: RiskScoreRepositoryImpl): RiskScoreRepository
}
