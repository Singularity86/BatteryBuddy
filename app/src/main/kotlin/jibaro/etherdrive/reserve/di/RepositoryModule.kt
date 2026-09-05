package jibaro.etherdrive.reserve.di

import jibaro.etherdrive.reserve.data.repository.BatteryRepository
import jibaro.etherdrive.reserve.data.repository.BatteryRepositoryImpl
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
    abstract fun bindBatteryRepository(impl: BatteryRepositoryImpl): BatteryRepository
}
