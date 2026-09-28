package monster.greyde.kachalochka.core.data.db

import app.cash.sqldelight.db.SqlDriver

internal fun kachalochkaDatabase(driver: SqlDriver): KachalochkaDatabase =
    KachalochkaDatabase(
        driver = driver,
        machineAdapter = Machine.Adapter(updated_atAdapter = InstantColumnAdapter),
        machine_linkAdapter = Machine_link.Adapter(updated_atAdapter = InstantColumnAdapter),
        measureAdapter = Measure.Adapter(updated_atAdapter = InstantColumnAdapter),
        measurementAdapter =
            Measurement.Adapter(
                dayAdapter = CalendarDayColumnAdapter,
                updated_atAdapter = InstantColumnAdapter,
            ),
        outboxAdapter = Outbox.Adapter(enqueuedAtAdapter = InstantColumnAdapter),
        photoAdapter =
            Photo.Adapter(
                taken_atAdapter = InstantColumnAdapter,
                updated_atAdapter = InstantColumnAdapter,
            ),
        profileAdapter = Profile.Adapter(updated_atAdapter = InstantColumnAdapter),
        syncStateAdapter = SyncState.Adapter(lastPullAtAdapter = InstantColumnAdapter),
        visitAdapter =
            Visit.Adapter(
                recorded_atAdapter = InstantColumnAdapter,
                ended_atAdapter = InstantColumnAdapter,
                updated_atAdapter = InstantColumnAdapter,
                dayAdapter = CalendarDayColumnAdapter,
            ),
        workout_setAdapter =
            Workout_set.Adapter(
                recorded_atAdapter = InstantColumnAdapter,
                updated_atAdapter = InstantColumnAdapter,
            ),
    )
