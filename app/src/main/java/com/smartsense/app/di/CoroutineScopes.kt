package com.smartsense.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope{
    @Singleton
    class AppScope @Inject constructor() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}