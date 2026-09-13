package monster.greyde.kachalochka.ui.home

import androidx.lifecycle.ViewModel
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials

class HomeViewModel(
    credentials: SupabaseCredentials,
) : ViewModel() {
    val backendConfigured: Boolean = credentials.isConfigured
}
