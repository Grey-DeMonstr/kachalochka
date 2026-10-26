package monster.greyde.kachalochka.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.sync.VisitNormalization
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.account.Nickname
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.calendar.CalendarViewModel
import monster.greyde.kachalochka.ui.calendar.VisitFeedViewModel
import monster.greyde.kachalochka.ui.family.PARENT_CODE
import monster.greyde.kachalochka.ui.family.PendingGuardian
import monster.greyde.kachalochka.ui.family.PeopleViewModel
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.platformUtcOffset
import monster.greyde.kachalochka.ui.friends.FriendCalendarViewModel
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.FriendVisitViewModel
import monster.greyde.kachalochka.ui.friends.GroupViewModel
import monster.greyde.kachalochka.ui.friends.GroupsCache
import monster.greyde.kachalochka.ui.friends.GroupsViewModel
import monster.greyde.kachalochka.ui.friends.PendingJoin
import monster.greyde.kachalochka.ui.home.HomeViewModel
import monster.greyde.kachalochka.ui.machine.FriendMachineViewModel
import monster.greyde.kachalochka.ui.machine.LinkChooserViewModel
import monster.greyde.kachalochka.ui.machine.LinkUpkeep
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.MachineFormViewModel
import monster.greyde.kachalochka.ui.machine.MachineListViewModel
import monster.greyde.kachalochka.ui.machine.MachinePickerViewModel
import monster.greyde.kachalochka.ui.machine.MachinePlacementViewModel
import monster.greyde.kachalochka.ui.machine.MachineReads
import monster.greyde.kachalochka.ui.machine.MineChooserViewModel
import monster.greyde.kachalochka.ui.measures.BodyFatViewModel
import monster.greyde.kachalochka.ui.measures.MeasureViewModel
import monster.greyde.kachalochka.ui.measures.MeasurementFormViewModel
import monster.greyde.kachalochka.ui.measures.MeasuresViewModel
import monster.greyde.kachalochka.ui.photos.PhotoLoaders
import monster.greyde.kachalochka.ui.plans.Placement
import monster.greyde.kachalochka.ui.plans.PlanFormViewModel
import monster.greyde.kachalochka.ui.plans.PlansViewModel
import monster.greyde.kachalochka.ui.plans.VisitPlacementViewModel
import monster.greyde.kachalochka.ui.settings.AccountViewModel
import monster.greyde.kachalochka.ui.settings.ProfileViewModel
import monster.greyde.kachalochka.ui.settings.SettingsViewModel
import monster.greyde.kachalochka.ui.stats.StatisticsViewModel
import monster.greyde.kachalochka.ui.timer.RestSoundScheduler
import monster.greyde.kachalochka.ui.timer.RestTimer
import monster.greyde.kachalochka.ui.timer.Ticker
import monster.greyde.kachalochka.ui.visit.VisitViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

val appModule =
    module {
        includes(coreModule)
        single<Clock> { Clock.System }
        single<Ticker> { Ticker { delay(1.seconds) } }
        single<UtcOffset> { UtcOffset(::platformUtcOffset) }
        singleOf(::DeviceClock)
        single { RestTimer(get()) }
        singleOf(::RestSoundScheduler)
        single {
            val offset: UtcOffset = get()
            VisitNormalizer(get(), get(), get(), offset::at)
        }
        singleOf(::VisitNormalization)
        singleOf(::PendingJoin)
        single { PendingGuardian(get(named(PARENT_CODE)), get()) }
        singleOf(::Nickname)
        singleOf(::FriendColorStore)
        singleOf(::PhotoLoaders)
        singleOf(::AccountAvatars)
        singleOf(::MachineCatalogue)
        singleOf(::MachineReads)
        singleOf(::LinkUpkeep)
        singleOf(::Placement)
        singleOf(::UnsavedChoices)
        single {
            GroupsCache(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
        viewModelOf(::HomeViewModel)
        viewModelOf(::GroupsViewModel)
        viewModelOf(::AccountsViewModel)
        viewModelOf(::CalendarViewModel)
        viewModelOf(::VisitFeedViewModel)
        viewModelOf(::MachineListViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::ProfileViewModel)
        viewModelOf(::AccountViewModel)
        viewModelOf(::MeasuresViewModel)
        viewModelOf(::PlansViewModel)
        viewModelOf(::PeopleViewModel)
        viewModelOf(::MachinePlacementViewModel)
        viewModelOf(::VisitPlacementViewModel)
        viewModelOf(::BodyFatViewModel)
        viewModelOf(::MeasureViewModel)
        viewModelOf(::VisitViewModel)
        viewModelOf(::MachineFormViewModel)
        viewModelOf(::MineChooserViewModel)
        viewModelOf(::FriendMachineViewModel)
        viewModelOf(::FriendVisitViewModel)
        viewModelOf(::FriendCalendarViewModel)
        viewModelOf(::GroupViewModel)
        // Koin finds a runtime parameter by its type, which a null has none of, so a screen that
        // can pass a null wraps its values in one args object.
        viewModelOf(::MeasurementFormViewModel)
        viewModelOf(::MachinePickerViewModel)
        viewModelOf(::PlanFormViewModel)
        viewModelOf(::StatisticsViewModel)
        viewModelOf(::LinkChooserViewModel)
    }
