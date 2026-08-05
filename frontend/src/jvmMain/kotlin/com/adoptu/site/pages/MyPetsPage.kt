package com.adoptu.site.pages

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
            div { id = "adoption-requests-section"; classes = setOf("mb-2rem")
                h2 { attributes["data-i18n"] = "adoptionRequests"; +"Adoption Requests" }
                div { id = "adoption-requests"; +"" }
            }
            div { id = "form-container"; classes = setOf("hidden")
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
                    div(classes = "fee-input-group") {
                        input(InputType.number) { id = "adoptionFee"; classes = setOf("fee-amount-wide"); step = "0.01"; value = "0"; this.min = "0" }
                        select { id = "currency"
                            option { value = "USD"; +"$ USD" }
                            option { value = "EUR"; +"€ EUR" }
                            option { value = "GBP"; +"£ GBP" }
                            option { value = "CAD"; +"$ CAD" }
                            option { value = "AUD"; +"$ AUD" }
                            option { value = "MXN"; +"$ MXN" }
                            option { value = "ARS"; +"$ ARS" }
                            option { value = "CLP"; +"$ CLP" }
                            option { value = "COP"; +"$ COP" }
                            option { value = "BRL"; +"R$ BRL" }
                            option { value = "PEN"; +"S/ PEN" }
                            option { value = "UYU"; +"$ UYU" }
                            option { value = "PYG"; +"₲ PYG" }
                            option { value = "BOB"; +"Bs BOB" }
                            option { value = "VES"; +"Bs VES" }
                            option { value = "CRC"; +"₡ CRC" }
                            option { value = "GTQ"; +"Q GTQ" }
                            option { value = "HNL"; +"L HNL" }
                            option { value = "NIO"; +"C\$ NIO" }
                            option { value = "DOP"; +"RD\$ DOP" }
                            option { value = "PAB"; +"B/. PAB" }
                        }
                    }
                    div { classes = setOf("checkbox-group")
                        input(InputType.checkBox) { id = "isUrgent" }; label { htmlFor = "isUrgent"; attributes["data-i18n"] = "urgentAdoption"; +"Urgent adoption needed" }
                    }
                    div { classes = setOf("checkbox-group")
                        input(InputType.checkBox) { id = "isPromoted" }; label { htmlFor = "isPromoted"; attributes["data-i18n"] = "needsNewHome"; +"This pet urgently needs a new home" }
                    }
                    div { id = "promoted-reason-row"; classes = setOf("hidden")
                        label { htmlFor = "promotedReason"; attributes["data-i18n"] = "promotedReasonLabel"; +"Reason" }
                        select { id = "promotedReason"
                            option { value = ""; attributes["data-i18n"] = "selectReason"; +"Select a reason" }
                            option { value = "MOVING"; attributes["data-i18n"] = "promotedReasonMoving"; +"Owner is moving" }
                            option { value = "COMPLAINTS"; attributes["data-i18n"] = "promotedReasonComplaints"; +"Complaints / can't keep more pets" }
                            option { value = "PET_CONFLICT"; attributes["data-i18n"] = "promotedReasonPetConflict"; +"Conflict with another pet" }
                            option { value = "OTHER"; attributes["data-i18n"] = "promotedReasonOther"; +"Other" }
                        }
                        label { htmlFor = "promotedReasonDetail"; attributes["data-i18n"] = "promotedReasonDetailLabel"; +"Additional details (optional)" }
                        textArea { id = "promotedReasonDetail" }
                    }
                    label { attributes["data-i18n"] = "photos"; +"Photos (max 12)" }
                    div(classes = "storage-dropzone") {
                        id = "storage-dropzone"
                        div { classes = setOf("dropzone-content"); attributes["data-i18n"] = "dropImagesHint"; +"Drop images here or click to browse" }
                        input(InputType.file) { id = "pet-images"; accept = "storage/*"; multiple = true; classes = setOf("file-input") }
                    }
                    div { id = "storage-previews"; classes = setOf("storage-previews") }
                    div(classes = "form-row") {
                        label { htmlFor = "pet-video"; attributes["data-i18n"] = "video"; +"Video (optional)" }
                        input(InputType.file) { id = "pet-video"; accept = "video/mp4,video/webm" }
                        div { id = "existing-video"; +"" }
                    }
                    div(classes = "form-actions") {
                        button(classes = "btn", type = ButtonType.submit) { attributes["data-i18n"] = "save"; +"Save" }
                        button(classes = "btn btn-secondary", type = ButtonType.button) { id = "cancel-btn"; attributes["data-i18n"] = "cancel"; +"Cancel" }
                    }
                }
                div { id = "medical-events-section"; classes = setOf("hidden", "card-bg", "profile-section")
                    h3 { attributes["data-i18n"] = "medicalSchedule"; +"Vaccination & Deworming Schedule" }
                    div { id = "medical-events-list"; +"" }
                    div(classes = "form-row") {
                        label { htmlFor = "medical-category"; attributes["data-i18n"] = "recordType"; +"Type" }
                        select { id = "medical-category"
                            option { value = "VACCINATION"; attributes["data-i18n"] = "vaccination"; +"Vaccination" }
                            option { value = "DEWORMING"; attributes["data-i18n"] = "deworming"; +"Deworming" }
                        }
                        label { htmlFor = "medical-name"; attributes["data-i18n"] = "recordName"; +"Name" }
                        input(InputType.text) { id = "medical-name"; placeholder = "e.g. Rabies" }
                        label { htmlFor = "medical-administered-date"; attributes["data-i18n"] = "dateGiven"; +"Date given" }
                        input(InputType.date) { id = "medical-administered-date" }
                        label { htmlFor = "medical-next-due-date"; attributes["data-i18n"] = "nextDueDate"; +"Next due (optional)" }
                        input(InputType.date) { id = "medical-next-due-date" }
                        label { htmlFor = "medical-notes"; attributes["data-i18n"] = "notesOptional"; +"Notes (optional)" }
                        input(InputType.text) { id = "medical-notes" }
                        p { id = "medical-event-message"; +"" }
                        button(classes = "btn btn-secondary", type = ButtonType.button) { id = "add-medical-event-btn"; attributes["data-i18n"] = "addRecord"; +"Add Record" }
                    }
                }
                div { id = "foster-placement-section"; classes = setOf("hidden", "card-bg", "profile-section")
                    h3 { attributes["data-i18n"] = "fosterPlacement"; +"Foster Placement" }
                    p { id = "foster-placement-status"; +"" }
                    button(classes = "btn btn-secondary hidden", type = ButtonType.button) { id = "end-placement-btn"; attributes["data-i18n"] = "endPlacement"; +"End Placement" }
                }
            }
            button(classes = "btn") { id = "add-btn"; attributes["data-i18n"] = "addPet"; +"Add Pet" }
            div { id = "pets"; classes = setOf("pet-grid", "mt-2rem"); +"" }
        }
        footer()
        commonScripts()
    }
}
