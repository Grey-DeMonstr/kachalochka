package monster.greyde.kachalochka.ui.account

/**
 * Where Google should send the browser back to: the page itself, since GitHub Pages serves the app
 * under a path the bare origin would miss. Supabase appends its own answer, so the page's is shed.
 */
fun signInReturnAddress(pageAddress: String): String =
    pageAddress.substringBefore('#').substringBefore('?')
