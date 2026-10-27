package com.panel.app.script

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StandardScriptFeatureModule {
    @Binds
    abstract fun bindLocalScriptFeature(impl: NoopLocalScriptFeature): LocalScriptFeature
}
