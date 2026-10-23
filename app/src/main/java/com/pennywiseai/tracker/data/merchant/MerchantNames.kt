// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-23 - see NOTICE.
package com.pennywiseai.tracker.data.merchant

/**
 * Turns a bank's POS or eCom string into the name shown on a transaction.
 *
 * A known shop wins with its brand name ("KFC-KURUNEGALA" → "KFC"). Anything
 * else keeps the bank's text, with a trailing city, terminal code, or legal
 * suffix taken off ("SERENTY INN HOMAGAMA" → "Serenty Inn").
 */
fun cleanMerchantName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    canonicalName(trimmed)?.let { return it }
    return tidyUnknown(trimmed)
}

private fun canonicalName(raw: String): String? {
    val merchant = forMatch(raw)
    var best: String? = null
    var bestLength = 0
    for ((keyword, name) in CANONICAL) {
        val key = forMatch(keyword)
        if (key.length <= bestLength) continue
        val hit = if (key.all { it.isLetterOrDigit() }) {
            Regex("\\b${Regex.escape(key)}\\b").containsMatchIn(merchant)
        } else {
            merchant.contains(key)
        }
        if (hit) {
            best = name
            bestLength = key.length
        }
    }
    return best
}

/** Same separator and glued-city cleanup as category matching, for lookup only. */
private fun forMatch(raw: String): String {
    val separated = raw.lowercase()
        .replace(Regex("[_*/&]+"), " ")
        .replace(Regex("-+"), " ")
    val split = GLUED_PLACE.replace(separated) { " ${it.value}" }
    return split.replace(Regex("\\s+"), " ").trim()
}

private fun tidyUnknown(raw: String): String {
    var name = raw.replace(Regex("\\s+"), " ").trim()
    repeat(8) {
        val next = name
            .replace(FUSED_TRAILING_CITY, "")
            .replace(TRAILING_CITY, "")
            .replace(TRAILING_CODE, "")
            .replace(Regex("[\\s&(/_-]+$"), "")
            .trim()
        if (next.isEmpty() || next == name) return@repeat
        name = next
    }
    if (name.isEmpty()) name = raw.trim()
    return if (name == name.uppercase()) titleCase(name) else name
}

private fun titleCase(name: String): String =
    name.split(" ").joinToString(" ") { word ->
        if (word.isEmpty()) word
        else word.substring(0, 1).uppercase() + word.substring(1).lowercase()
    }

private const val CITIES =
    "kurunegala|homagama|colombo|pitipana|baddegama|nuwaraeliya|" +
        "maharagama|negombo|kandy|galle|matara|jaffna|gampaha|kelaniya|" +
        "kaduwela|nugegoda|dehiwala|moratuwa|ratnapura|anuradhapura|" +
        "batticaloa|trincomalee|kalutara|panadura|wattala|kiribathgoda|" +
        "malabe|rajagiriya|chilaw|puttalam|horana|beruwala|hikkaduwa"

private val GLUED_PLACE = Regex("(?<=[a-z])($CITIES)(?![a-z])")

/** "RESKURUNEGALA" / "CHomagama": a short stub fused onto a city at the end. */
private val FUSED_TRAILING_CITY = Regex("(?i)(?:^|\\s)[a-z]{1,3}(?:$CITIES)(?:\\s+\\d{1,4})?$")
private val TRAILING_CITY = Regex("(?i)\\s+(?:$CITIES)(?:\\s+\\d{1,4})?$")
private val TRAILING_CODE = Regex("(?i)\\s+(?:lk|plc|pvt|ltd|limited|ku|ma|kglk|\\d{6,})$")

private val CANONICAL = mapOf(
    "dialog axiata" to "Dialog",
    "dialog tv" to "Dialog",
    "dialog broadband" to "Dialog",
    "dialog" to "Dialog",
    "mobitel" to "Mobitel",
    "sri lanka telecom" to "SLT",
    "peotv" to "SLT",
    "peo tv" to "SLT",
    "slt" to "SLT",
    "hutchison" to "Hutch",
    "hutch" to "Hutch",
    "airtel" to "Airtel",
    "lanka bell" to "Lanka Bell",
    "keells" to "Keells",
    "keels" to "Keells",
    "jaykay" to "Keells",
    "food city" to "Cargills Food City",
    "cargills" to "Cargills Food City",
    "richard pieris" to "Arpico",
    "arpico" to "Arpico",
    "glomark" to "Glomark",
    "laugfs super" to "Laugfs Super",
    "laugfs gas" to "Laugfs Gas",
    "laugfs" to "Laugfs",
    "spar" to "SPAR",
    "lanka sathosa" to "Sathosa",
    "sathosa" to "Sathosa",
    "finagle" to "Finagle",
    "perera and sons" to "Perera & Sons",
    "perera & sons" to "Perera & Sons",
    "perera - sons" to "Perera & Sons",
    "the fab" to "Fab",
    "fab" to "Fab",
    "barista" to "Barista",
    "java lounge" to "Java Lounge",
    "sen-saal" to "Sen-Saal",
    "sensaal" to "Sen-Saal",
    "sponge" to "Sponge",
    "dinemore" to "Dinemore",
    "kfc" to "KFC",
    "mcdonald's" to "McDonald's",
    "mcdonalds" to "McDonald's",
    "pizza hut" to "Pizza Hut",
    "burger king" to "Burger King",
    "subway" to "Subway",
    "domino's" to "Domino's",
    "dominos" to "Domino's",
    "popeyes" to "Popeyes",
    "bread talk" to "BreadTalk",
    "breadtalk" to "BreadTalk",
    "uber eats" to "Uber Eats",
    "uber" to "Uber",
    "pickme" to "PickMe",
    "ceylon electricity" to "CEB",
    "ceb" to "CEB",
    "lanka electricity" to "LECO",
    "leco" to "LECO",
    "national water" to "NWSDB",
    "water board" to "NWSDB",
    "nwsdb" to "NWSDB",
    "litro" to "Litro",
    "kangaroo cabs" to "Kangaroo Cabs",
    "kangaroo" to "Kangaroo Cabs",
    "ceylon petroleum" to "Ceypetco",
    "ceypetco" to "Ceypetco",
    "lanka ioc" to "Lanka IOC",
    "sinopec" to "Sinopec",
    "shell" to "Shell",
    "daraz" to "Daraz",
    "kapruka" to "Kapruka",
    "odel" to "Odel",
    "no limit" to "Nolimit",
    "nolimit" to "Nolimit",
    "cool planet" to "Cool Planet",
    "fashion bug" to "Fashion Bug",
    "softlogic life" to "Softlogic Life",
    "softlogic" to "Softlogic",
    "abans" to "Abans",
    "singer mega" to "Singer Mega",
    "singer" to "Singer",
    "damro" to "Damro",
    "singhagiri" to "Singhagiri",
    "takas" to "Takas",
    "wasi" to "Wasi",
    "simplytek" to "SimplyTek",
    "idealz" to "iDealz",
    "spa ceylon" to "Spa Ceylon",
    "barefoot" to "Barefoot",
    "laksala" to "Laksala",
    "dsi" to "DSI",
    "bata" to "Bata",
    "sarasavi" to "Sarasavi",
    "vijitha yapa" to "Vijitha Yapa",
    "mydeal" to "MyDeal",
    "healthguard" to "Healthguard",
    "osu sala" to "Osu Sala",
    "osusala" to "Osu Sala",
    "asiri" to "Asiri",
    "nawaloka" to "Nawaloka",
    "durdans" to "Durdans",
    "hemas" to "Hemas",
    "ninewells" to "Ninewells",
    "doc990" to "Doc990",
    "echannelling" to "eChannelling",
    "vision care" to "Vision Care",
    "scope entertainment" to "Scope Cinemas",
    "scope cinemas" to "Scope Cinemas",
    "mytickets" to "MyTickets",
    "google one" to "Google One",
    "spotify" to "Spotify",
    "netflix" to "Netflix",
    "youtube" to "YouTube",
    "microsoft" to "Microsoft",
    "mintpay" to "Mintpay",
    "paykoko" to "Koko",
    "koko" to "Koko",
    "payhere" to "PayHere",
    "webxpay" to "WebXPay",
    "frimi" to "FriMi",
    "genie" to "Genie",
    "ez cash" to "eZ Cash",
    "ezcash" to "eZ Cash",
    "sliit" to "SLIIT",
    "nsbm" to "NSBM",
    "nibm" to "NIBM",
    "iit" to "IIT",
    "esoft" to "ESOFT",
    "apiit" to "APIIT",
    "icbt" to "ICBT",
    "british council" to "British Council",
    "sri lanka insurance" to "Sri Lanka Insurance",
    "ceylinco" to "Ceylinco",
    "fairfirst" to "Fairfirst",
    "janashakthi" to "Janashakthi",
    "sampath" to "Sampath Bank",
    "commercial bank" to "Commercial Bank",
    "combank" to "Commercial Bank",
    "hatton national" to "HNB",
    "hnb" to "HNB",
    "bank of ceylon" to "Bank of Ceylon",
    "boc" to "Bank of Ceylon",
    "people's bank" to "People's Bank",
    "peoples bank" to "People's Bank",
    "seylan" to "Seylan Bank",
    "nations trust" to "Nations Trust Bank",
    "ndb" to "NDB",
    "dfcc" to "DFCC",
    "nsb" to "NSB",
    "pan asia" to "Pan Asia Bank"
)
