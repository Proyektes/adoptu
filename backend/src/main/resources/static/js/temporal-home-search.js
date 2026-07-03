window.searchTemporalHomes = async function() {
    const params = window.buildLocationSearchParams() || new URLSearchParams();
    
    try {
        const response = await fetch('/api/temporal-homes?' + params.toString());
        if (!response.ok) throw new Error('Search failed');
        const homes = await response.json();
        displayResults(homes);
    } catch (err) {
        console.error(err);
        document.getElementById('results-container').innerHTML = '<p>Error loading results.</p>';
    }
};

window.displayResults = function(homes) {
    const container = document.getElementById('results-container');
    if (!homes.length) {
        container.innerHTML = '<p data-i18n="noTemporalHomes">No temporal homes found.</p>';
        return;
    }
    container.innerHTML = homes.map(home => '<div class="temporal-home-card"><h3>' + (home.alias || 'Temporal Home') + '</h3><p>' + home.city + ', ' + home.state + ', ' + tCountry(home.country) + '</p><a href="/temporal-home/' + home.id + '" data-i18n="viewDetails">View Details</a></div>').join('');
};

document.addEventListener('DOMContentLoaded', () => {
    const searchBtn = document.getElementById('search-btn');
    if (searchBtn) {
        searchBtn.addEventListener('click', window.searchTemporalHomes);
    }
    const stateInput = document.getElementById('search-state');
    if (stateInput) {
        stateInput.addEventListener('input', debounce(window.searchTemporalHomes, 500));
    }
    const cityInput = document.getElementById('search-city');
    if (cityInput) {
        cityInput.addEventListener('input', debounce(window.searchTemporalHomes, 500));
    }
    const zipInput = document.getElementById('search-zip');
    if (zipInput) {
        zipInput.addEventListener('input', debounce(window.searchTemporalHomes, 500));
    }
    const neighborhoodInput = document.getElementById('search-neighborhood');
    if (neighborhoodInput) {
        neighborhoodInput.addEventListener('input', debounce(window.searchTemporalHomes, 500));
    }
});

window.debounce = function(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}