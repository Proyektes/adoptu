package com.adoptu.common

import java.text.Normalizer

enum class Country(val displayName: String, val i18nKey: String, val alpha2: String) {
    AFGHANISTAN("Afghanistan", "country.afghanistan", "AF"),
    ALBANIA("Albania", "country.albania", "AL"),
    ALGERIA("Algeria", "country.algeria", "DZ"),
    ARGENTINA("Argentina", "country.argentina", "AR"),
    ARMENIA("Armenia", "country.armenia", "AM"),
    AUSTRALIA("Australia", "country.australia", "AU"),
    AUSTRIA("Austria", "country.austria", "AT"),
    AZERBAIJAN("Azerbaijan", "country.azerbaijan", "AZ"),
    BANGLADESH("Bangladesh", "country.bangladesh", "BD"),
    BELARUS("Belarus", "country.belarus", "BY"),
    BELGIUM("Belgium", "country.belgium", "BE"),
    BOLIVIA("Bolivia", "country.bolivia", "BO"),
    BOSNIA_AND_HERZEGOVINA("Bosnia and Herzegovina", "country.bosnia", "BA"),
    BRAZIL("Brazil", "country.brazil", "BR"),
    BULGARIA("Bulgaria", "country.bulgaria", "BG"),
    CAMBODIA("Cambodia", "country.cambodia", "KH"),
    CAMEROON("Cameroon", "country.cameroon", "CM"),
    CANADA("Canada", "country.canada", "CA"),
    CHILE("Chile", "country.chile", "CL"),
    CHINA("China", "country.china", "CN"),
    COLOMBIA("Colombia", "country.colombia", "CO"),
    COSTA_RICA("Costa Rica", "country.costaRica", "CR"),
    CROATIA("Croatia", "country.croatia", "HR"),
    CUBA("Cuba", "country.cuba", "CU"),
    CZECH_REPUBLIC("Czech Republic", "country.czechia", "CZ"),
    DENMARK("Denmark", "country.denmark", "DK"),
    DOMINICAN_REPUBLIC("Dominican Republic", "country.dominicanRepublic", "DO"),
    ECUADOR("Ecuador", "country.ecuador", "EC"),
    EGYPT("Egypt", "country.egypt", "EG"),
    EL_SALVADOR("El Salvador", "country.elSalvador", "SV"),
    ESTONIA("Estonia", "country.estonia", "EE"),
    ETHIOPIA("Ethiopia", "country.ethiopia", "ET"),
    FINLAND("Finland", "country.finland", "FI"),
    FRANCE("France", "country.france", "FR"),
    GEORGIA("Georgia", "country.georgia", "GE"),
    GERMANY("Germany", "country.germany", "DE"),
    GHANA("Ghana", "country.ghana", "GH"),
    GREECE("Greece", "country.greece", "GR"),
    GUATEMALA("Guatemala", "country.guatemala", "GT"),
    HAITI("Haiti", "country.haiti", "HT"),
    HONDURAS("Honduras", "country.honduras", "HN"),
    HUNGARY("Hungary", "country.hungary", "HU"),
    ICELAND("Iceland", "country.iceland", "IS"),
    INDIA("India", "country.india", "IN"),
    INDONESIA("Indonesia", "country.indonesia", "ID"),
    IRAN("Iran", "country.iran", "IR"),
    IRAQ("Iraq", "country.iraq", "IQ"),
    IRELAND("Ireland", "country.ireland", "IE"),
    ISRAEL("Israel", "country.israel", "IL"),
    ITALY("Italy", "country.italy", "IT"),
    JAMAICA("Jamaica", "country.jamaica", "JM"),
    JAPAN("Japan", "country.japan", "JP"),
    JORDAN("Jordan", "country.jordan", "JO"),
    KAZAKHSTAN("Kazakhstan", "country.kazakhstan", "KZ"),
    KENYA("Kenya", "country.kenya", "KE"),
    KUWAIT("Kuwait", "country.kuwait", "KW"),
    LATVIA("Latvia", "country.latvia", "LV"),
    LEBANON("Lebanon", "country.lebanon", "LB"),
    LIBYA("Libya", "country.libya", "LY"),
    LITHUANIA("Lithuania", "country.lithuania", "LT"),
    LUXEMBOURG("Luxembourg", "country.luxembourg", "LU"),
    MALAYSIA("Malaysia", "country.malaysia", "MY"),
    MEXICO("Mexico", "country.mexico", "MX"),
    MOLDOVA("Moldova", "country.moldova", "MD"),
    MONGOLIA("Mongolia", "country.mongolia", "MN"),
    MONTENEGRO("Montenegro", "country.montenegro", "ME"),
    MOROCCO("Morocco", "country.morocco", "MA"),
    MYANMAR("Myanmar", "country.myanmar", "MM"),
    NEPAL("Nepal", "country.nepal", "NP"),
    NETHERLANDS("Netherlands", "country.netherlands", "NL"),
    NEW_ZEALAND("New Zealand", "country.newZealand", "NZ"),
    NICARAGUA("Nicaragua", "country.nicaragua", "NI"),
    NIGERIA("Nigeria", "country.nigeria", "NG"),
    NORTH_KOREA("North Korea", "country.northKorea", "KP"),
    NORWAY("Norway", "country.norway", "NO"),
    PAKISTAN("Pakistan", "country.pakistan", "PK"),
    PANAMA("Panama", "country.panama", "PA"),
    PARAGUAY("Paraguay", "country.paraguay", "PY"),
    PERU("Peru", "country.peru", "PE"),
    PHILIPPINES("Philippines", "country.philippines", "PH"),
    POLAND("Poland", "country.poland", "PL"),
    PORTUGAL("Portugal", "country.portugal", "PT"),
    PUERTO_RICO("Puerto Rico", "country.puertoRico", "PR"),
    QATAR("Qatar", "country.qatar", "QA"),
    ROMANIA("Romania", "country.romania", "RO"),
    RUSSIA("Russia", "country.russia", "RU"),
    SAUDI_ARABIA("Saudi Arabia", "country.saudiArabia", "SA"),
    SERBIA("Serbia", "country.serbia", "RS"),
    SINGAPORE("Singapore", "country.singapore", "SG"),
    SLOVAKIA("Slovakia", "country.slovakia", "SK"),
    SLOVENIA("Slovenia", "country.slovenia", "SI"),
    SOUTH_AFRICA("South Africa", "country.southAfrica", "ZA"),
    SOUTH_KOREA("South Korea", "country.southKorea", "KR"),
    SPAIN("Spain", "country.spain", "ES"),
    SRI_LANKA("Sri Lanka", "country.sriLanka", "LK"),
    SUDAN("Sudan", "country.sudan", "SD"),
    SWEDEN("Sweden", "country.sweden", "SE"),
    SWITZERLAND("Switzerland", "country.switzerland", "CH"),
    SYRIA("Syria", "country.syria", "SY"),
    TAIWAN("Taiwan", "country.taiwan", "TW"),
    THAILAND("Thailand", "country.thailand", "TH"),
    TUNISIA("Tunisia", "country.tunisia", "TN"),
    TURKEY("Turkey", "country.turkey", "TR"),
    UKRAINE("Ukraine", "country.ukraine", "UA"),
    UNITED_ARAB_EMIRATES("United Arab Emirates", "country.uae", "AE"),
    UNITED_KINGDOM("United Kingdom", "country.uk", "GB"),
    UNITED_STATES("United States", "country.usa", "US"),
    URUGUAY("Uruguay", "country.uruguay", "UY"),
    UZBEKISTAN("Uzbekistan", "country.uzbekistan", "UZ"),
    VENEZUELA("Venezuela", "country.venezuela", "VE"),
    VIETNAM("Vietnam", "country.vietnam", "VN"),
    YEMEN("Yemen", "country.yemen", "YE");

    companion object {
        private val byNormalizedName: Map<String, Country> = entries.associateBy { normalize(it.displayName) }
        private val byAlpha2: Map<String, Country> = entries.associateBy { it.alpha2 }

        fun fromDisplayName(raw: String?): Country? {
            if (raw.isNullOrBlank()) return null
            entries.firstOrNull { it.displayName == raw }?.let { return it }
            return byNormalizedName[normalize(raw)]
        }

        fun fromIso2(code: String?): Country? {
            if (code.isNullOrBlank()) return null
            return byAlpha2[code.trim().uppercase()]
        }

        private fun normalize(value: String): String =
            Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .lowercase()
    }
}
