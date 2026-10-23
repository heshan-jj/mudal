// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-23 - see NOTICE.
package com.pennywiseai.tracker.categorization

import com.pennywiseai.parser.core.bank.BankParser
import com.pennywiseai.parser.core.bank.CommercialBankParser
import com.pennywiseai.parser.core.bank.SampathBankParser
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.merchant.cleanMerchantName
import com.pennywiseai.tracker.ui.icons.BrandIcons
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Category, icon and display name for Sri Lankan merchants.
 *
 * The first test parses every SMS in docs/lk-samples. The second walks the
 * brand list, including the messy POS spellings banks actually send.
 */
class SriLankaMerchantDetectionTest {

    @Test
    fun `every merchant in the real sms fixtures lands on the right category and icon`() {
        val seen = linkedSetOf<String>()
        parseFixtures("sampath.txt", "SAMPATHTXN", SampathBankParser()).forEach { seen += it }
        parseFixtures("commercial-bank.txt", "COMBANK", CommercialBankParser()).forEach { seen += it }

        val missing = seen.filter { it !in FIXTURES.keys }
        val unused = FIXTURES.keys.filter { it !in seen }
        assertTrue("parsed merchants with no expectation: $missing", missing.isEmpty())
        assertTrue("expectations never produced by a fixture: $unused", unused.isEmpty())
        val problems = mismatches(seen.map { it to FIXTURES.getValue(it) })
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `sri lankan brands keep a category an icon and a clean name`() {
        assertTrue("brand list should cover the chains we ship icons for", CATALOG.size >= 100)
        val problems = mismatches(CATALOG.entries.map { it.key to it.value })
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun mismatches(rows: List<Pair<String, Detection>>): List<String> =
        rows.mapNotNull { (merchant, expected) ->
            val category = SharedCategoryMapping.getCategory(merchant)
            val icon = BrandIcons.getIconResource(merchant)
            val name = cleanMerchantName(merchant)
            if (category == expected.category && icon == expected.icon && name == expected.display) null
            else "$merchant => [$category / $icon / $name] expected [${expected.category} / ${expected.icon} / ${expected.display}]"
        }

    private fun parseFixtures(fileName: String, sender: String, parser: BankParser): List<String> {
        val bodies = messages(fixture(fileName))
        assertTrue("$fileName had no messages", bodies.isNotEmpty())
        return bodies.map { body ->
            val parsed = parser.parse(body, sender, 1_700_000_000_000L)
            assertNotNull("did not parse:\n$body", parsed)
            parsed!!.merchant ?: error("no merchant:\n$body")
        }
    }

    private fun fixture(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val current = dir ?: return@repeat
            val file = File(current, "docs/lk-samples/$fileName")
            if (file.isFile) return file.readText()
            dir = current.parentFile
        }
        error("docs/lk-samples/$fileName not found from ${System.getProperty("user.dir")}")
    }

    private fun messages(text: String): List<String> =
        text.split(Regex("(?:\\r?\\n){2,}"))
            .map { block ->
                block.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .joinToString("\n")
            }
            .filter { it.isNotEmpty() }
}

private data class Detection(val category: String, val icon: Int?, val display: String)

private fun shop(category: String, icon: Int, display: String) = Detection(category, icon, display)

private fun other(display: String) = Detection("Others", null, display)

private val FIXTURES = mapOf(
    "ATM - Makumbura MMC" to other("ATM - Makumbura MMC"),
    "OWNER-Cash" to other("OWNER-Cash"),
    "PickMe" to shop("Transportation", R.drawable.ic_brand_pickme, "PickMe"),
    "Expenses" to other("Expenses"),
    "cash" to other("cash"),
    "Seylan card" to shop("Banking", R.drawable.ic_brand_seylan, "Seylan Bank"),
    "Dialog Axiata PLC" to shop("Bills & Utilities", R.drawable.ic_brand_dialog, "Dialog"),
    "KFC-KURUNEGALA" to shop("Food & Dining", R.drawable.ic_brand_kfc, "KFC"),
    "Google Spotify Music" to shop("Entertainment", R.drawable.ic_brand_spotify, "Spotify"),
    "ATM - ICBS" to other("Atm - Icbs"),
    "ATM - NUWARAELIYA BRANCH" to other("Atm - Nuwaraeliya Branch"),
    "OWNER-loan" to Detection("Banking", null, "OWNER-loan"),
    "Entertainment" to other("Entertainment"),
    "HUTCHISON TELECOM LANKA" to shop("Bills & Utilities", R.drawable.ic_brand_hutch, "Hutch"),
    "PERERA - SONS BAKERS" to shop("Food & Dining", R.drawable.ic_brand_perera_sons, "Perera & Sons"),
    "OWNER-H" to other("Owner-h"),
    "TFR OWNER" to other("Tfr Owner"),
    "OWNER" to other("Owner"),
    "Netflix" to shop("Entertainment", R.drawable.ic_brand_netflix, "Netflix"),
    "PERSON A" to other("Person A"),
    "tzploaded" to other("tzploaded"),
    "Bank Interest" to Detection("Banking", null, "Bank Interest"),
    "RANSARA RESTAURANT" to other("Ransara Restaurant").copy(category = "Food & Dining"),
    "Ransiri Bake And House" to other("Ransiri Bake And House").copy(category = "Food & Dining"),
    "CEFT Transfer" to other("CEFT Transfer"),
    "Transfer Inward" to other("Transfer Inward"),
    "Google One" to shop("Entertainment", R.drawable.ic_brand_google, "Google One"),
    "ATM - NSB BADDEGAMA ATM LK" to shop("Banking", R.drawable.ic_brand_nsb_bank, "NSB"),
    "OWNER-bus" to other("OWNER-bus"),
    "keels" to shop("Groceries", R.drawable.ic_brand_keells, "Keells"),
    "Visa Recurring Payment" to other("Visa Recurring Payment"),
    "MINTPAY" to shop("Shopping", R.drawable.ic_brand_mintpay, "Mintpay"),
    "Microsoft*Store" to shop("Shopping", R.drawable.ic_brand_microsoft, "Microsoft"),
    "boat" to other("boat"),
    "TFR OWNER cash" to other("TFR OWNER cash"),
    "PERSON B" to other("Person B"),
    "DIGITAL BANKING DIVISION" to other("Digital Banking Division"),
    "TERRA LOUNGE CAFE & RESKURUNEGALA KGLK" to other("Terra Lounge Cafe").copy(category = "Food & Dining"),
    "ATM - ICBS AKUB3 KU BZ3" to other("Atm - Icbs Akub3 Ku Bz3"),
    "SERENTY INN HOMAGAMA" to other("Serenty Inn").copy(category = "Food & Dining"),
    "PERERA & SONS BAKERS PVT HOMAGAMA" to shop("Food & Dining", R.drawable.ic_brand_perera_sons, "Perera & Sons"),
    "Finagle Minimart _ NSBM CHomagama" to shop("Groceries", R.drawable.ic_brand_finagle, "Finagle"),
    "H P H DULANJAYA PITIPANA" to other("H P H Dulanjaya"),
    "HUTCHISON TELECOM LANKA COLOMBO 04" to shop("Bills & Utilities", R.drawable.ic_brand_hutch, "Hutch")
)

private val CATALOG = mapOf(
    "Dialog Axiata PLC" to shop("Bills & Utilities", R.drawable.ic_brand_dialog, "Dialog"),
    "DIALOG TV" to shop("Bills & Utilities", R.drawable.ic_brand_dialog, "Dialog"),
    "Mobitel Reload" to shop("Bills & Utilities", R.drawable.ic_brand_mobitel, "Mobitel"),
    "Sri Lanka Telecom" to shop("Bills & Utilities", R.drawable.ic_brand_slt, "SLT"),
    "PEO TV" to shop("Bills & Utilities", R.drawable.ic_brand_slt, "SLT"),
    "HUTCHISON TELECOM LANKA COLOMBO 04" to shop("Bills & Utilities", R.drawable.ic_brand_hutch, "Hutch"),
    "AIRTEL" to shop("Bills & Utilities", R.drawable.ic_brand_airtel, "Airtel"),
    "Lanka Bell" to shop("Bills & Utilities", R.drawable.ic_brand_lanka_bell, "Lanka Bell"),
    "keels" to shop("Groceries", R.drawable.ic_brand_keells, "Keells"),
    "JAYKAY MARKETING" to shop("Groceries", R.drawable.ic_brand_keells, "Keells"),
    "CARGILLS FOOD CITY" to shop("Groceries", R.drawable.ic_brand_cargills, "Cargills Food City"),
    "ARPICO SUPER CENTRE" to shop("Groceries", R.drawable.ic_brand_arpico, "Arpico"),
    "Richard Pieris" to shop("Groceries", R.drawable.ic_brand_arpico, "Arpico"),
    "GLOMARK" to shop("Groceries", R.drawable.ic_brand_glomark, "Glomark"),
    "LAUGFS SUPER" to shop("Groceries", R.drawable.ic_brand_laugfs, "Laugfs Super"),
    "SPAR" to shop("Groceries", R.drawable.ic_brand_spar, "SPAR"),
    "LANKA SATHOSA" to shop("Groceries", R.drawable.ic_brand_sathosa, "Sathosa"),
    "Finagle Minimart _ NSBM CHomagama" to shop("Groceries", R.drawable.ic_brand_finagle, "Finagle"),
    "PERERA - SONS BAKERS" to shop("Food & Dining", R.drawable.ic_brand_perera_sons, "Perera & Sons"),
    "PERERA & SONS BAKERS PVT HOMAGAMA" to shop("Food & Dining", R.drawable.ic_brand_perera_sons, "Perera & Sons"),
    "THE FAB" to shop("Food & Dining", R.drawable.ic_brand_fab, "Fab"),
    "BARISTA" to shop("Food & Dining", R.drawable.ic_brand_barista, "Barista"),
    "JAVA LOUNGE" to shop("Food & Dining", R.drawable.ic_brand_java_lounge, "Java Lounge"),
    "SEN-SAAL" to shop("Food & Dining", R.drawable.ic_brand_sensaal, "Sen-Saal"),
    "SPONGE" to shop("Food & Dining", R.drawable.ic_brand_sponge, "Sponge"),
    "DINEMORE" to shop("Food & Dining", R.drawable.ic_brand_dinemore, "Dinemore"),
    "KFC-KURUNEGALA" to shop("Food & Dining", R.drawable.ic_brand_kfc, "KFC"),
    "McDonald's" to shop("Food & Dining", R.drawable.ic_brand_mcdonalds, "McDonald's"),
    "PIZZA HUT" to shop("Food & Dining", R.drawable.ic_brand_pizza_hut, "Pizza Hut"),
    "BURGER KING" to shop("Food & Dining", R.drawable.ic_brand_burger_king, "Burger King"),
    "SUBWAY" to shop("Food & Dining", R.drawable.ic_brand_subway, "Subway"),
    "Domino's" to shop("Food & Dining", R.drawable.ic_brand_dominos, "Domino's"),
    "POPEYES" to shop("Food & Dining", R.drawable.ic_brand_popeyes, "Popeyes"),
    "BREADTALK" to shop("Food & Dining", R.drawable.ic_brand_breadtalk, "BreadTalk"),
    "Uber Eats" to shop("Food & Dining", R.drawable.ic_brand_uber, "Uber Eats"),
    "PickMe" to shop("Transportation", R.drawable.ic_brand_pickme, "PickMe"),
    "Uber" to shop("Transportation", R.drawable.ic_brand_uber, "Uber"),
    "KANGAROO CABS" to shop("Transportation", R.drawable.ic_brand_kangaroo, "Kangaroo Cabs"),
    "CEYPETCO" to shop("Transportation", R.drawable.ic_brand_ceypetco, "Ceypetco"),
    "LANKA IOC" to shop("Transportation", R.drawable.ic_brand_lanka_ioc, "Lanka IOC"),
    "SINOPEC" to shop("Transportation", R.drawable.ic_brand_sinopec, "Sinopec"),
    "SHELL" to shop("Transportation", R.drawable.ic_brand_shell, "Shell"),
    "CEB" to shop("Bills & Utilities", R.drawable.ic_brand_ceb, "CEB"),
    "Ceylon Electricity" to shop("Bills & Utilities", R.drawable.ic_brand_ceb, "CEB"),
    "LECO" to shop("Bills & Utilities", R.drawable.ic_brand_leco, "LECO"),
    "NWSDB" to shop("Bills & Utilities", R.drawable.ic_brand_nwsdb, "NWSDB"),
    "Water Board" to shop("Bills & Utilities", R.drawable.ic_brand_nwsdb, "NWSDB"),
    "LITRO" to shop("Bills & Utilities", R.drawable.ic_brand_litro, "Litro"),
    "LAUGFS GAS" to shop("Bills & Utilities", R.drawable.ic_brand_laugfs, "Laugfs Gas"),
    "DARAZ" to shop("Shopping", R.drawable.ic_brand_daraz, "Daraz"),
    "KAPRUKA" to shop("Shopping", R.drawable.ic_brand_kapruka, "Kapruka"),
    "ODEL" to shop("Shopping", R.drawable.ic_brand_odel, "Odel"),
    "NO LIMIT" to shop("Shopping", R.drawable.ic_brand_nolimit, "Nolimit"),
    "COOL PLANET" to shop("Shopping", R.drawable.ic_brand_cool_planet, "Cool Planet"),
    "FASHION BUG" to shop("Shopping", R.drawable.ic_brand_fashion_bug, "Fashion Bug"),
    "SOFTLOGIC" to shop("Shopping", R.drawable.ic_brand_softlogic, "Softlogic"),
    "Softlogic Life" to shop("Insurance", R.drawable.ic_brand_softlogic, "Softlogic Life"),
    "ABANS" to shop("Shopping", R.drawable.ic_brand_abans, "Abans"),
    "SINGER MEGA" to shop("Shopping", R.drawable.ic_brand_singer, "Singer Mega"),
    "DAMRO" to shop("Shopping", R.drawable.ic_brand_damro, "Damro"),
    "SINGHAGIRI" to shop("Shopping", R.drawable.ic_brand_singhagiri, "Singhagiri"),
    "TAKAS" to shop("Shopping", R.drawable.ic_brand_takas, "Takas"),
    "WASI" to shop("Shopping", R.drawable.ic_brand_wasi, "Wasi"),
    "SIMPLYTEK" to shop("Shopping", R.drawable.ic_brand_simplytek, "SimplyTek"),
    "IDEALZ" to shop("Shopping", R.drawable.ic_brand_idealz, "iDealz"),
    "BAREFOOT" to shop("Shopping", R.drawable.ic_brand_barefoot, "Barefoot"),
    "LAKSALA" to shop("Shopping", R.drawable.ic_brand_laksala, "Laksala"),
    "DSI" to shop("Shopping", R.drawable.ic_brand_dsi, "DSI"),
    "BATA" to shop("Shopping", R.drawable.ic_brand_bata, "Bata"),
    "SARASAVI" to shop("Shopping", R.drawable.ic_brand_sarasavi, "Sarasavi"),
    "VIJITHA YAPA" to shop("Shopping", R.drawable.ic_brand_vijitha_yapa, "Vijitha Yapa"),
    "MYDEAL" to shop("Shopping", R.drawable.ic_brand_mydeal, "MyDeal"),
    "Spa Ceylon" to shop("Personal Care", R.drawable.ic_brand_spa_ceylon, "Spa Ceylon"),
    "HEALTHGUARD" to shop("Healthcare", R.drawable.ic_brand_healthguard, "Healthguard"),
    "OSU SALA" to shop("Healthcare", R.drawable.ic_brand_osusala, "Osu Sala"),
    "ASIRI" to shop("Healthcare", R.drawable.ic_brand_asiri, "Asiri"),
    "NAWALOKA" to shop("Healthcare", R.drawable.ic_brand_nawaloka, "Nawaloka"),
    "DURDANS" to shop("Healthcare", R.drawable.ic_brand_durdans, "Durdans"),
    "HEMAS" to shop("Healthcare", R.drawable.ic_brand_hemas, "Hemas"),
    "NINEWELLS" to shop("Healthcare", R.drawable.ic_brand_ninewells, "Ninewells"),
    "DOC990" to shop("Healthcare", R.drawable.ic_brand_doc990, "Doc990"),
    "ECHANNELLING" to shop("Healthcare", R.drawable.ic_brand_echannelling, "eChannelling"),
    "VISION CARE" to shop("Healthcare", R.drawable.ic_brand_vision_care, "Vision Care"),
    "SCOPE CINEMAS" to shop("Entertainment", R.drawable.ic_brand_scope, "Scope Cinemas"),
    "MYTICKETS" to shop("Entertainment", R.drawable.ic_brand_mytickets, "MyTickets"),
    "Google One" to shop("Entertainment", R.drawable.ic_brand_google, "Google One"),
    "Google Spotify Music" to shop("Entertainment", R.drawable.ic_brand_spotify, "Spotify"),
    "NETFLIX" to shop("Entertainment", R.drawable.ic_brand_netflix, "Netflix"),
    "YOUTUBE" to shop("Entertainment", R.drawable.ic_brand_youtube, "YouTube"),
    "Microsoft*Store" to shop("Shopping", R.drawable.ic_brand_microsoft, "Microsoft"),
    "MINTPAY" to shop("Shopping", R.drawable.ic_brand_mintpay, "Mintpay"),
    "PAYKOKO" to shop("Shopping", R.drawable.ic_brand_koko, "Koko"),
    "PAYHERE" to shop("Shopping", R.drawable.ic_brand_payhere, "PayHere"),
    "WEBXPAY" to shop("Shopping", R.drawable.ic_brand_webxpay, "WebXPay"),
    "FRIMI" to shop("Banking", R.drawable.ic_brand_frimi, "FriMi"),
    "GENIE" to shop("Banking", R.drawable.ic_brand_genie, "Genie"),
    "EZ CASH" to shop("Banking", R.drawable.ic_brand_ezcash, "eZ Cash"),
    "SLIIT" to shop("Education", R.drawable.ic_brand_sliit, "SLIIT"),
    "NSBM" to shop("Education", R.drawable.ic_brand_nsbm, "NSBM"),
    "NIBM" to shop("Education", R.drawable.ic_brand_nibm, "NIBM"),
    "IIT" to shop("Education", R.drawable.ic_brand_iit, "IIT"),
    "ESOFT" to shop("Education", R.drawable.ic_brand_esoft, "ESOFT"),
    "APIIT" to shop("Education", R.drawable.ic_brand_apiit, "APIIT"),
    "ICBT" to shop("Education", R.drawable.ic_brand_icbt, "ICBT"),
    "British Council" to shop("Education", R.drawable.ic_brand_british_council, "British Council"),
    "CEYLINCO" to shop("Insurance", R.drawable.ic_brand_ceylinco, "Ceylinco"),
    "Sri Lanka Insurance" to shop("Insurance", R.drawable.ic_brand_slic, "Sri Lanka Insurance"),
    "FAIRFIRST" to shop("Insurance", R.drawable.ic_brand_fairfirst, "Fairfirst"),
    "JANASHAKTHI" to shop("Insurance", R.drawable.ic_brand_janashakthi, "Janashakthi"),
    "SAMPATH" to shop("Banking", R.drawable.ic_brand_sampath, "Sampath Bank"),
    "COMBANK" to shop("Banking", R.drawable.ic_brand_combank, "Commercial Bank"),
    "HNB" to shop("Banking", R.drawable.ic_brand_hnb, "HNB"),
    "Bank of Ceylon" to shop("Banking", R.drawable.ic_brand_boc, "Bank of Ceylon"),
    "People's Bank" to shop("Banking", R.drawable.ic_brand_peoples, "People's Bank"),
    "Nations Trust" to shop("Banking", R.drawable.ic_brand_ntb, "Nations Trust Bank"),
    "NDB" to shop("Banking", R.drawable.ic_brand_ndb_bank, "NDB"),
    "DFCC" to shop("Banking", R.drawable.ic_brand_dfcc, "DFCC"),
    "NSB" to shop("Banking", R.drawable.ic_brand_nsb_bank, "NSB"),
    "PAN ASIA" to shop("Banking", R.drawable.ic_brand_pan_asia, "Pan Asia Bank")
)
