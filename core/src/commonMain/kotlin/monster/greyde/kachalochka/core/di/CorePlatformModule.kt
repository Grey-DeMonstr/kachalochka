package monster.greyde.kachalochka.core.di

import org.koin.core.module.Module

/** Binds the repository implementation the running target persists through. */
expect fun corePlatformModule(): Module
