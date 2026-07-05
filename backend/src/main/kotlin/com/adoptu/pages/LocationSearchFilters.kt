package com.adoptu.pages

import kotlinx.html.*

fun DIV.locationSearchFilters(
    formClass: String = "location-search-form",
    includeNeighborhood: Boolean = true
) {
    div(classes = formClass) {
        p(classes = "location-search-hint") {
            attributes["data-i18n"] = "selectCountryFirst"
            +"Select a country to enable filters"
        }
        div(classes = "location-search-country") {
            label { htmlFor = "search-country"; attributes["data-i18n"] = "countryLabel"; +"Country" }
            select {
                id = "search-country"
                name = "country"
                onChange = "window.onCountryChange()"
                countrySelect("search-country", true, "selectCountryToSearch")
            }
        }
        div(classes = "location-search-filters") {
            div(classes = "location-search-filter") {
                label { htmlFor = "search-state"; attributes["data-i18n"] = "state"; +"State" }
                input(InputType.text) { name = "state"; id = "search-state"; disabled = true }
            }
            div(classes = "location-search-filter") {
                label { htmlFor = "search-city"; attributes["data-i18n"] = "city"; +"City" }
                input(InputType.text) { name = "city"; id = "search-city"; disabled = true }
            }
            div(classes = "location-search-filter") {
                label { htmlFor = "search-zip"; attributes["data-i18n"] = "zipCode"; +"Zip" }
                input(InputType.text) { name = "zip"; id = "search-zip"; disabled = true; maxLength = "7" }
            }
            if (includeNeighborhood) {
                div(classes = "location-search-filter") {
                    label { htmlFor = "search-neighborhood"; attributes["data-i18n"] = "neighborhood"; +"Neighborhood" }
                    input(InputType.text) { name = "neighborhood"; id = "search-neighborhood"; disabled = true }
                }
            }
        }
        script {
            unsafe {
                raw("""
window.onCountryChange = function() {
    var sel = document.getElementById('search-country');
    var hasCountry = sel && sel.value.length > 0;
    if (hasCountry) {
        try { localStorage.setItem('adoptuSelectedCountry', sel.value); } catch (e) {}
    }
    var ids = ['search-state', 'search-city', 'search-zip', 'search-neighborhood'];
    ids.forEach(function(id) {
        var el = document.getElementById(id);
        if (!el) return;
        el.disabled = !hasCountry;
        if (!hasCountry) el.value = '';
    });
    var hint = document.querySelector('.location-search-hint');
    if (hint) hint.style.display = hasCountry ? 'none' : '';
};
// Shared helper: build URLSearchParams from the filter fields.
// Returns null if country is not selected.
window.buildLocationSearchParams = function() {
    var country = (document.getElementById('search-country') || {}).value || '';
    if (!country) return null;
    var params = new URLSearchParams();
    params.append('country', country);
    var state = (document.getElementById('search-state') || {}).value;
    var city = (document.getElementById('search-city') || {}).value;
    var zip = (document.getElementById('search-zip') || {}).value;
    var neighborhood = (document.getElementById('search-neighborhood') || {}).value;
    if (state) params.append('state', state);
    if (city) params.append('city', city);
    if (zip) params.append('zip', zip);
    if (neighborhood) params.append('neighborhood', neighborhood);
    return params;
};
// Restore the last country picked on any search page (Shelters/Photographers/
// Sterilization/TemporalHome all share this component), so switching pages
// doesn't force re-selecting the same country.
(function() {
    var sel = document.getElementById('search-country');
    if (sel && !sel.value) {
        var saved = null;
        try { saved = localStorage.getItem('adoptuSelectedCountry'); } catch (e) {}
        if (saved) sel.value = saved;
    }
    window.onCountryChange();
})();
""")
            }
        }
    }
}