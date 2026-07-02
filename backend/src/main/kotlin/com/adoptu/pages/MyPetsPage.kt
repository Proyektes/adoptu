package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

fun HTML.myPetsPage(navParams: NavParams = NavParams()) {
    commonHead("My Pets - Adopt-U", "mypets.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            h1 { attributes["data-i18n"] = "myPets"; +"My Pets" }
            div { id = "message"; +"" }
            div { id = "adoption-requests-section"; style = "margin-bottom: 2rem;"
                h2 { attributes["data-i18n"] = "adoptionRequests"; +"Adoption Requests" }
                div { id = "adoption-requests"; +"" }
            }
            div { id = "form-container"; style = "display:none"
                h2 { id = "form-title"; attributes["data-i18n"] = "addPet"; +"Add Pet" }
                form { id = "pet-form"
                    input(InputType.hidden) { id = "pet-id" }
                    label { htmlFor = "name"; attributes["data-i18n"] = "name"; +"Name *" }; input(InputType.text) { id = "name"; required = true }
                    label { htmlFor = "type"; attributes["data-i18n"] = "type"; +"Type *" }; select { id = "type"; required = true
                        option { value = "DOG"; attributes["data-i18n"] = "dog"; +"Dog" }
                        option { value = "CAT"; attributes["data-i18n"] = "cat"; +"Cat" }
                        option { value = "BIRD"; attributes["data-i18n"] = "bird"; +"Bird" }
                        option { value = "FISH"; attributes["data-i18n"] = "fish"; +"Fish" }
                    }
                    label { htmlFor = "breed"; attributes["data-i18n"] = "breed"; +"Breed" }; input(InputType.text) { id = "breed" }
                    label { htmlFor = "description"; attributes["data-i18n"] = "description"; +"Description" }; textArea { id = "description" }
                    label { htmlFor = "weight"; attributes["data-i18n"] = "weight"; +"Weight (kg)" }; input(InputType.number) { id = "weight"; step = "0.01"; value = "0"; this.min = "0" }
                    label { htmlFor = "ageYears"; attributes["data-i18n"] = "ageYears"; +"Age (years)" }; input(InputType.number) { id = "ageYears"; value = "0"; this.min = "0" }
                    label { htmlFor = "ageMonths"; attributes["data-i18n"] = "ageMonths"; +"Age (months)" }; input(InputType.number) { id = "ageMonths"; value = "0"; this.min = "0"; this.max = "11" }
                    label { htmlFor = "sex"; attributes["data-i18n"] = "sex"; +"Sex" }; select { id = "sex"
                        option { value = "MALE"; attributes["data-i18n"] = "male"; +"Male" }
                        option { value = "FEMALE"; attributes["data-i18n"] = "female"; +"Female" }
                    }
                    label { htmlFor = "color"; attributes["data-i18n"] = "color"; +"Color" }; input(InputType.text) { id = "color" }
                    label { htmlFor = "size"; attributes["data-i18n"] = "size"; +"Size" }; select { id = "size"
                        option { value = ""; attributes["data-i18n"] = "selectSize"; +"Select size" }
                        option { value = "SMALL"; attributes["data-i18n"] = "small"; +"Small" }
                        option { value = "MEDIUM"; attributes["data-i18n"] = "medium"; +"Medium" }
                        option { value = "LARGE"; attributes["data-i18n"] = "large"; +"Large" }
                    }
                    label { htmlFor = "temperament"; attributes["data-i18n"] = "temperament"; +"Temperament" }; input(InputType.text) { id = "temperament" }
                    label { htmlFor = "energyLevel"; attributes["data-i18n"] = "energyLevel"; +"Energy Level" }; select { id = "energyLevel"
                        option { value = ""; attributes["data-i18n"] = "selectEnergy"; +"Select energy" }
                        option { value = "LOW"; attributes["data-i18n"] = "low"; +"Low" }
                        option { value = "MEDIUM"; +"Medium" }
                        option { value = "HIGH"; attributes["data-i18n"] = "high"; +"High" }
                    }
                    label { attributes["data-i18n"] = "medical"; +"Medical" }
                    div { classes = setOf("checkbox-group")
                        input(InputType.checkBox) { id = "isSterilized" }; label { htmlFor = "isSterilized"; attributes["data-i18n"] = "sterilized"; +"Sterilized" }
                        input(InputType.checkBox) { id = "isMicrochipped" }; label { htmlFor = "isMicrochipped"; attributes["data-i18n"] = "microchipped"; +"Microchipped" }
                    }
                    label { htmlFor = "microchipId"; attributes["data-i18n"] = "microchipId"; +"Microchip ID" }; input(InputType.text) { id = "microchipId" }
                    label { htmlFor = "vaccinations"; attributes["data-i18n"] = "vaccinations"; +"Vaccinations" }; textArea { id = "vaccinations" }
                    label { attributes["data-i18n"] = "compatibility"; +"Compatibility" }
                    div { classes = setOf("checkbox-group")
                        input(InputType.checkBox) { id = "isGoodWithKids"; checked = true }; label { htmlFor = "isGoodWithKids"; attributes["data-i18n"] = "goodWithKids"; +"Good with kids" }
                        input(InputType.checkBox) { id = "isGoodWithDogs"; checked = true }; label { htmlFor = "isGoodWithDogs"; attributes["data-i18n"] = "goodWithDogs"; +"Good with dogs" }
                        input(InputType.checkBox) { id = "isGoodWithCats"; checked = true }; label { htmlFor = "isGoodWithCats"; attributes["data-i18n"] = "goodWithCats"; +"Good with cats" }
                        input(InputType.checkBox) { id = "isHouseTrained" }; label { htmlFor = "isHouseTrained"; attributes["data-i18n"] = "houseTrained"; +"House trained" }
                    }
                    label { htmlFor = "rescueLocation"; attributes["data-i18n"] = "rescueLocation"; +"Rescue Location" }; input(InputType.text) { id = "rescueLocation" }
                    label { htmlFor = "rescueDate"; attributes["data-i18n"] = "rescueDate"; +"Rescue Date" }; input(InputType.date) { id = "rescueDate" }
                    label { htmlFor = "specialNeeds"; attributes["data-i18n"] = "specialNeeds"; +"Special Needs" }; textArea { id = "specialNeeds" }
                    label { htmlFor = "adoptionFee"; attributes["data-i18n"] = "adoptionFee"; +"Adoption Fee" }; 
                    div(classes = "fee-input-group") { style = "display: flex; gap: 0.5rem;"
                        input(InputType.number) { id = "adoptionFee"; step = "0.01"; value = "0"; style = "flex: 3;"; this.min = "0" }
                        select { id = "currency"; style = "flex: 1;"
                            option { value = "USD"; +"$ USD" }
                            option { value = "EUR"; +"€ EUR" }
                            option { value = "GBP"; +"£ GBP" }
                            option { value = "CAD"; +"$ CAD" }
                            option { value = "AUD"; +"$ AUD" }
                        }
                    }
                    div { classes = setOf("checkbox-group")
                        input(InputType.checkBox) { id = "isUrgent" }; label { htmlFor = "isUrgent"; attributes["data-i18n"] = "urgentAdoption"; +"Urgent adoption needed" }
                    }
                    label { attributes["data-i18n"] = "photos"; +"Photos (max 12)" }
                    div(classes = "storage-dropzone") {
                        id = "storage-dropzone"
                        div { classes = setOf("dropzone-content"); +"Drop images here or click to browse" }
                        input(InputType.file) { id = "pet-images"; accept = "storage/*"; multiple = true; classes = setOf("file-input") }
                    }
                    div { id = "storage-previews"; classes = setOf("storage-previews") }
                    div(classes = "form-actions") {
                        button(classes = "btn", type = ButtonType.submit) { attributes["data-i18n"] = "save"; +"Save" }
                        button(classes = "btn btn-secondary", type = ButtonType.button) { id = "cancel-btn"; attributes["data-i18n"] = "cancel"; +"Cancel" }
                    }
                }
            }
            button(classes = "btn") { id = "add-btn"; attributes["data-i18n"] = "addPet"; +"Add Pet" }
            div { id = "pets"; classes = setOf("pet-grid"); style = "margin-top: 2rem;"; +"" }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
