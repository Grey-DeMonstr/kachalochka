package monster.greyde.kachalochka.navigation

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/** The `footer` of index.html, which Compose leaves alone because it draws only into `#app`. */
class DocumentFooter : PageFooter {
    override fun show(visible: Boolean) {
        val footer = document.querySelector("footer") as? HTMLElement ?: return
        footer.style.display = if (visible) "" else "none"
    }
}
