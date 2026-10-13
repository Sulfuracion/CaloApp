package com.controlcal.app.di

import com.controlcal.app.data.repo.DailyLogRepositoryImpl
import com.controlcal.app.data.repo.GoalProfileRepositoryImpl
import com.controlcal.app.data.repo.MealEntryRepositoryImpl
import com.controlcal.app.data.repo.ProductRepositoryImpl
import com.controlcal.core.nutrition.CalculateDayRemainingUseCase
import com.controlcal.core.nutrition.GetDayTotalsUseCase
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindMealEntryRepository(impl: MealEntryRepositoryImpl): MealEntryRepository

    @Binds
    @Singleton
    abstract fun bindDailyLogRepository(impl: DailyLogRepositoryImpl): DailyLogRepository

    @Binds
    @Singleton
    abstract fun bindGoalProfileRepository(impl: GoalProfileRepositoryImpl): GoalProfileRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetDayTotalsUseCase(mealEntryRepository: MealEntryRepository): GetDayTotalsUseCase =
        GetDayTotalsUseCase(mealEntryRepository)

    @Provides
    @Singleton
    fun provideCalculateDayRemainingUseCase(): CalculateDayRemainingUseCase =
        CalculateDayRemainingUseCase()
}
