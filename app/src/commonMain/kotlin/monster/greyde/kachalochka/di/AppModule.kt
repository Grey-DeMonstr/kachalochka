package monster.greyde.kachalochka.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.sync.VisitNormalization
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.account.Nickname
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.calendar.CalendarViewModel
import monster.greyde.kachalochka.ui.family.ChildrenViewModel
import monster.greyde.kachalochka.ui.family.GuardiansViewModel
import monster.greyde.kachalochka.ui.family.PARENT_CODE
import monster.greyde.kachalochka.ui.family.PendingGuardian
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
import monster.greyde.kachalochka.ui.machine.MachineFormArgs
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
import monster.greyde.kachalochka.ui.settings.SettingsViewModel
import monster.greyde.kachalochka.ui.stats.StatisticsViewModel
import monster.greyde.kachalochka.ui.timer.RestSoundScheduler
import monster.greyde.kachalochka.ui.timer.RestTimer
import monster.greyde.kachalochka.ui.timer.Ticker
import monster.greyde.kachalochka.ui.visit.VisitViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
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
        viewModelOf(::MachineListViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::MeasuresViewModel)
        viewModelOf(::PlansViewModel)
        viewModelOf(::ChildrenViewModel)
        viewModelOf(::GuardiansViewModel)
        viewModelOf(::MachinePlacementViewModel)
        viewModelOf(::VisitPlacementViewModel)
        viewModel { (day: CalendarDay?) ->
            MeasurementFormViewModel(day, get(), get(), get(), get(), get(), get(), get(), get())
        }
        viewModel { (method: BodyFatMethod) ->
            BodyFatViewModel(method, get(), get(), get(), get(), get(), get(), get(), get())
        }
        viewModel { (measure: MeasureId) ->
            MeasureViewModel(measure, get(), get(), get(), get(), get(), get(), get(), get())
        }
        viewModel { (day: CalendarDay) ->
            VisitViewModel(
                day,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (day: CalendarDay?) ->
            MachinePickerViewModel(
                day,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (plan: PlanId?, fromVisit: CalendarDay?, machine: MachineId?) ->
            PlanFormViewModel(
                plan,
                fromVisit,
                machine,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (machine: MachineId?) ->
            StatisticsViewModel(
                machine,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (args: MachineFormArgs) ->
            MachineFormViewModel(
                args,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (machine: MachineId, start: String?) ->
            LinkChooserViewModel(
                machine,
                start,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (machine: MachineId, name: String) ->
            MineChooserViewModel(machine, name, get(), get(), get(), get(), get())
        }
        viewModel { (machine: MachineId, owner: UserId) ->
            FriendMachineViewModel(
                machine,
                owner,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (member: UserId, name: String, day: CalendarDay) ->
            FriendVisitViewModel(
                member,
                name,
                day,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { (member: UserId) ->
            FriendCalendarViewModel(member, get(), get(), get(), get(), get(), get(), get())
        }
        viewModel { (group: GroupId) ->
            GroupViewModel(group, get(), get(), get(), get(), get(), get())
        }
    }
