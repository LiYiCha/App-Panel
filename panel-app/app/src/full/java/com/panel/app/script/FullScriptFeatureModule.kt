package com.panel.app.script

import com.panel.app.script.LocalScriptFeature
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FullScriptFeatureModule {
    @Binds
    abstract fun bindLocalScriptFeature(impl: DefaultLocalScriptFeature): LocalScriptFeature
}
