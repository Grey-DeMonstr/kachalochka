package monster.greyde.kachalochka.legal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Gradle runs jvmTest in the module directory and declares this directory as its input.
private val webResources = File("src/wasmJsMain/resources")

private const val ISSUES = "https://github.com/Grey-DeMonstr/kachalochka/issues"

private val DOCUMENTS = listOf("privacy.html", "terms.html")
private val PAGES = DOCUMENTS + "index.html"

// Whitespace collapsed, so wrapping the HTML source never breaks a phrase a test looks for.
private fun page(name: String): String =
    File(webResources, name).readText().replace(Regex("\\s+"), " ")

private fun headings(html: String): List<String> =
    Regex("<h2>(.*?)</h2>").findAll(html).map { it.groupValues[1] }.toList()

class LegalPagesTest {
    @Test
    fun privacyPolicyHasEverySection() {
        assertEquals(
            listOf(
                "Summary",
                "What we collect",
                "Where it is stored",
                "Who can see it",
                "Service providers",
                "Retention and deletion",
                "Security",
                "Permissions",
                "Children",
                "Changes",
                "Contact",
            ),
            headings(page("privacy.html")),
        )
    }

    @Test
    fun privacyPolicyExplainsHowToDeleteAnAccount() {
        val html = page("privacy.html")
        assertTrue("“Удалить аккаунт” (Delete account) in the app’s settings" in html)
        assertTrue("$ISSUES/new" in html)
        assertTrue("within 30 days" in html)
    }

    @Test
    fun privacyPolicyTellsThatAndroidSignOutKeepsRecordsOnTheDevice() {
        assertTrue("its records also stay on the device" in page("privacy.html"))
    }

    @Test
    fun privacyPolicyDisclosesBackupAndTheFirstSignInUpload() {
        val html = page("privacy.html")
        assertTrue("Android’s own backup" in html)
        assertTrue("When you first sign in, what you recorded without an account" in html)
    }

    @Test
    fun privacyPolicyListsWhatGoogleSignInStores() {
        val html = page("privacy.html")
        assertTrue("profile picture link" in html)
        assertTrue("records when you sign in and from which IP address" in html)
    }

    @Test
    fun privacyPolicyStatesTheManifestPermissions() {
        assertTrue("asks for no runtime permissions" in page("privacy.html"))
    }

    @Test
    fun privacyPolicyKeepsDeletedGroupsAndFriendsCopiesInRetention() {
        val html = page("privacy.html")
        assertTrue("groups you delete or leave" in html)
        assertTrue("copied from you belongs to their account" in html)
    }

    @Test
    fun privacyPolicyKeepsBodyMeasuresPrivate() {
        val html = page("privacy.html")
        assertTrue("<strong>Body measures:</strong>" in html)
        assertTrue("never see your body measures or your profile details" in html)
    }

    @Test
    fun privacyPolicyListsTheProfile() {
        val html = page("privacy.html")
        assertTrue("your nickname" in html)
        assertTrue("sex, year of birth and height" in html)
        assertTrue("the colours you pick for friends" in html)
    }

    @Test
    fun privacyPolicyCoversSharingAVisitAsText() {
        assertTrue("When you share a visit as text" in page("privacy.html"))
    }

    @Test
    fun privacyPolicySetsTheAgeAndExplainsGuardianLinks() {
        val html = page("privacy.html")
        assertTrue("for people aged 13 and over" in html)
        assertTrue("<strong>Guardian links:</strong>" in html)
        assertTrue("never see your body measures, your profile details or your groups" in html)
        assertTrue("You or the guardian can end the link at any time" in html)
    }

    @Test
    fun termsSetTheAgeAndExplainGuardianLinks() {
        val html = page("terms.html")
        assertTrue("for people aged 13 and over" in html)
        assertTrue("A guardian you link by entering their code" in html)
    }

    @Test
    fun termsSayBodyFatIsAnEstimate() {
        assertTrue("estimates, not measurements" in page("terms.html"))
    }

    @Test
    fun termsSayTheInviteCodeCannotBeChanged() {
        val html = page("terms.html")
        assertTrue("The code cannot be changed and members cannot be removed" in html)
    }

    @Test
    fun termsHaveEverySection() {
        assertEquals(
            listOf(
                "Agreement",
                "The service",
                "Your account",
                "Your content",
                "Acceptable use",
                "Groups",
                "Health",
                "No warranty",
                "Limitation of liability",
                "Ending use",
                "Changes",
                "Source code",
                "Contact",
            ),
            headings(page("terms.html")),
        )
    }

    @Test
    fun termsLeaveTheGplRightsIntact() {
        val html = page("terms.html")
        assertTrue("https://www.gnu.org/licenses/gpl-3.0.html" in html)
        assertTrue("do not limit the rights the GPL gives you" in html)
    }

    // The app draws on a canvas, so only plain HTML shows a crawler what the page is.
    @Test
    fun entryPageDescribesTheAppAndLinksToBothDocuments() {
        val html = page("index.html")
        assertTrue("<meta name=\"description\"" in html)
        assertTrue("<a href=\"privacy.html\">Privacy Policy</a>" in html)
        assertTrue("<a href=\"terms.html\">Terms of Service</a>" in html)
    }

    // Google's reviewer reads the homepage, not its meta tags.
    @Test
    fun entryPageFooterVisiblyDescribesTheApp() {
        val footer = page("index.html").substringAfter("<footer").substringBefore("</footer>")
        assertTrue("syncs between Android and the web" in footer)
    }

    @Test
    fun entryPageGivesTheAppItsOwnContainer() {
        assertTrue("<div id=\"app\"></div>" in page("index.html"))
    }

    @Test
    fun eachDocumentStatesItsEffectiveDate() {
        val date = Regex("""Effective date: <time datetime="\d{4}-\d{2}-\d{2}">""")
        for (name in DOCUMENTS) assertTrue(date.containsMatchIn(page(name)), name)
    }

    @Test
    fun eachDocumentNamesItsProviderAndContact() {
        for (name in DOCUMENTS) {
            val html = page(name)
            assertTrue("Sergei Ivanov" in html, name)
            assertTrue(ISSUES in html, name)
        }
    }

    @Test
    fun eachDocumentLinksToTheAppAndBothDocuments() {
        for (name in DOCUMENTS) {
            for (href in listOf("./", "privacy.html", "terms.html")) {
                assertTrue("href=\"$href\"" in page(name), "$name links to $href")
            }
        }
    }

    @Test
    fun documentsRunNoScript() {
        for (name in DOCUMENTS) assertFalse("<script" in page(name), name)
    }

    @Test
    fun pagesRenderOnAPhone() {
        for (name in PAGES) {
            val html = page(name)
            assertTrue("<html lang=\"en\">" in html, name)
            assertTrue("<meta charset=\"UTF-8\">" in html, name)
            assertTrue("width=device-width, initial-scale=1" in html, name)
        }
    }

    @Test
    fun pagesLoadNothingFromAnotherOrigin() {
        val offOrigin = Regex("""src="(https?:)?//""")
        val offOriginLink = Regex("""<link[^>]*href="(https?:)?//""")
        for (name in PAGES) {
            val html = page(name)
            assertFalse(offOriginLink.containsMatchIn(html), name)
            assertFalse("@import" in html, name)
            assertFalse("http://" in html, name)
            assertFalse(offOrigin.containsMatchIn(html), name)
        }
    }

    // Pages serves the app under a sub-path, where a root-relative link leaves the site.
    @Test
    fun linksStayRelative() {
        for (name in PAGES) assertFalse("href=\"/" in page(name), name)
    }

    @Test
    fun pagesHoldNoPlaceholders() {
        for (name in PAGES) {
            for (marker in listOf("TODO", "TBD", "lorem", "XXX")) {
                assertFalse(marker in page(name), "$name holds $marker")
            }
        }
    }
}
