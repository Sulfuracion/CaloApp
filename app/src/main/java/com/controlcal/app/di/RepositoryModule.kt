package com.controlcal.app.di

import com.controlcal.app.data.repo.DailyLogRepositoryImpl
import com.controlcal.app.data.repo.GoalProfileRepositoryImpl
import com.controlcal.app.data.repo.MealEntryRepositoryImpl
import com.controlcal.app.data.repo.ProductRepositoryImpl
import com.controlcal.app.data.repo.UserSettingsRepositoryImpl
import com.controlcal.app.data.repo.WeightEntryRepositoryImpl
import com.controlcal.core.nutrition.CalculateDayRemainingUseCase
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.nutrition.GetDayTotalsUseCase
import com.controlcal.core.nutrition.ResolveDayTypeUseCase
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.repo.UserSettingsRepository
import com.controlcal.core.repo.WeightEntryRepository
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

    @Binds
    @Singleton
    abstract fun bindWeightEntryRepository(impl: WeightEntryRepositoryImpl): WeightEntryRepository

    @Binds
    @Singleton
    abstract fun bindUserSettingsRepository(impl: UserSettingsRepositoryImpl): UserSettingsRepository

    @Binds
    @Singleton
    abstract fun bindMealConfigRepository(impl: com.controlcal.app.data.repo.MealConfigRepositoryImpl): com.controlcal.core.repo.MealConfigRepository
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

    @Provides
    @Singleton
    fun provideResolveDayTypeUseCase(
        dailyLogRepository: DailyLogRepository,
        userSettingsRepository: UserSettingsRepository,
    ): ResolveDayTypeUseCase =
        ResolveDayTypeUseCase(dailyLogRepository, userSettingsRepository)

    @Provides
    @Singleton
    fun provideGetActiveGoalProfileUseCase(
        goalProfileRepository: GoalProfileRepository,
    ): GetActiveGoalProfileUseCase =
        GetActiveGoalProfileUseCase(goalProfileRepository)
}
