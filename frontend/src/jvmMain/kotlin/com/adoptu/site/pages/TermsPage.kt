package com.adoptu.site.pages

import kotlinx.html.*

private fun UL.i18nLi(key: String) {
    li { attributes["data-i18n"] = key; +labelText[key]!! }
}

private val labelText: Map<String, String> = mapOf(
    "termsAcceptanceH2" to "Acceptance of Terms",
    "termsAcceptanceText" to "By accessing and using Adopt-U, you accept and agree to be bound by the terms and provisions of this agreement. If you do not agree to these terms, please do not use our platform.",
    "termsNatureH2" to "Nature of Our Service",
    "termsNatureText1" to "Adopt-U is a connection platform that facilitates contact between pet rescuers and potential adopters. We are not a pet store, broker, shelter, or adoption agency. We do not own, custody, or intermediately handle any pets listed on our platform.",
    "termsNatureText2" to "Our role is strictly limited to providing a communication channel between parties. We do not participate in adoption negotiations, contracts, or the physical transfer of animals.",
    "termsUserRespH2" to "User Responsibilities",
    "termsForRescuersH3" to "For Rescuers:",
    "termsRescuerResp1" to "Provide accurate, truthful information about pets in your care",
    "termsRescuerResp2" to "Ensure pets are healthy, vaccinated, and appropriately vetted before listing",
    "termsRescuerResp3" to "Conduct responsible screening of potential adopters",
    "termsRescuerResp4" to "Arrange safe transfer of pets to their new homes",
    "termsRescuerResp5" to "Accept responsibility for the accuracy of all information provided",
    "termsRescuerResp6" to "Comply with all local laws and regulations regarding pet adoption",
    "termsRescuerResp7" to "Never use the platform for commercial pet sales or breeding purposes",
    "termsForAdoptersH3" to "For Adopters:",
    "termsAdopterResp1" to "Provide accurate information about your living situation and experience with pets",
    "termsAdopterResp2" to "Be prepared to demonstrate your ability to provide proper care",
    "termsAdopterResp3" to "Understand that rescinding a pet's adoption causes significant stress to the animal",
    "termsAdopterResp4" to "Respect the rescuer's right to decline your adoption request",
    "termsPetListingsH2" to "Pet Listings and Information",
    "termsPetListingsText1" to "Adopt-U does not verify, endorse, or guarantee the accuracy of information provided in pet listings. Information including but not limited to breed, age, health status, temperament, and vaccination records is provided by rescuers and has not been independently verified.",
    "termsPetListingsText2" to "Photos and descriptions are provided by rescuers. While we prohibit deliberate misrepresentation, we cannot guarantee that all listings accurately represent the current condition of the animal.",
    "termsPetListingsText3" to "Users are encouraged to ask questions, request veterinary records, and meet pets before finalizing any adoption arrangement.",
    "termsAdoptionProcessH2" to "Adoption Process",
    "termsAdoptionProcessIntro" to "Once a rescuer and adopter agree to proceed with an adoption:",
    "termsAdoptionStep1" to "All arrangements, including transfer date, location, and any associated costs, are negotiated directly between the parties",
    "termsAdoptionStep2" to "Adopt-U does not facilitate or guarantee any payment transactions",
    "termsAdoptionStep3" to "The platform does not provide adoption contracts; any contractual arrangements are the sole responsibility of the parties involved",
    "termsAdoptionStep4" to "We strongly recommend documenting all agreements in writing",
    "termsLiabilityH2" to "Limitation of Liability",
    "termsLiabilityIntro" to "Adopt-U shall not be held liable for:",
    "termsLiability1" to "Any disputes, disagreements, or conflicts between rescuers and adopters",
    "termsLiability2" to "The accuracy of information provided in pet listings",
    "termsLiability3" to "The health, behavior, or condition of any animal before, during, or after adoption",
    "termsLiability4" to "Any financial losses, damages, or injuries arising from use of the platform",
    "termsLiability5" to "The outcome of any adoption, including but not limited to returns, health issues, or compatibility problems",
    "termsLiability6" to "Any actions taken by users outside of the platform after contact has been established",
    "termsLiabilityAck" to "Users acknowledge that adopting a pet is a significant commitment and that they assume full responsibility for their decision to adopt.",
    "termsProhibitedH2" to "Prohibited Activities",
    "termsProhibitedIntro" to "The following activities are strictly prohibited on Adopt-U:",
    "termsProhibited1" to "Commercial pet sales, breeding operations, or puppy mills",
    "termsProhibited2" to "Listing pets you do not directly care for",
    "termsProhibited3" to "False or misleading information in listings or profiles",
    "termsProhibited4" to "Harassment, discrimination, or inappropriate contact with other users",
    "termsProhibited5" to "Using the platform to collect personal information for purposes unrelated to pet adoption",
    "termsProhibited6" to "Any illegal activities or violations of animal welfare laws",
    "termsAccountTerminationH2" to "Account Termination",
    "termsAccountTerminationText" to "Adopt-U reserves the right to suspend or terminate accounts that violate these terms, engage in prohibited activities, or bring the platform into disrepute. We may also remove listings that contain false, misleading, or inappropriate content.",
    "termsModificationsH2" to "Modifications to Terms",
    "termsModificationsText" to "We reserve the right to modify these terms at any time. Continued use of the platform after changes constitutes acceptance of the modified terms. We will notify users of significant changes via email or platform announcements.",
    "termsContactText" to "If you have any questions about these Terms and Conditions, please contact us at admin@adopt-u.com."
)

fun HTML.termsPage(navParams: NavParams = NavParams()) {
    commonHead("Terms and Conditions - Adopt-U", "policy.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "policy-content") {
                h1 { attributes["data-i18n"] = "termsConditions"; +"Terms and Conditions" }
                p { attributes["data-i18n"] = "privacyLastUpdated"; +"Last updated: April 2025" }

                h2 { attributes["data-i18n"] = "termsAcceptanceH2"; +"Acceptance of Terms" }
                p { attributes["data-i18n"] = "termsAcceptanceText"; +"By accessing and using Adopt-U, you accept and agree to be bound by the terms and provisions of this agreement. If you do not agree to these terms, please do not use our platform." }

                h2 { attributes["data-i18n"] = "termsNatureH2"; +"Nature of Our Service" }
                p { attributes["data-i18n"] = "termsNatureText1"; +"Adopt-U is a connection platform that facilitates contact between pet rescuers and potential adopters. We are not a pet store, broker, shelter, or adoption agency. We do not own, custody, or intermediately handle any pets listed on our platform." }
                p { attributes["data-i18n"] = "termsNatureText2"; +"Our role is strictly limited to providing a communication channel between parties. We do not participate in adoption negotiations, contracts, or the physical transfer of animals." }

                h2 { attributes["data-i18n"] = "termsUserRespH2"; +"User Responsibilities" }
                h3 { attributes["data-i18n"] = "termsForRescuersH3"; +"For Rescuers:" }
                ul {
                    i18nLi("termsRescuerResp1")
                    i18nLi("termsRescuerResp2")
                    i18nLi("termsRescuerResp3")
                    i18nLi("termsRescuerResp4")
                    i18nLi("termsRescuerResp5")
                    i18nLi("termsRescuerResp6")
                    i18nLi("termsRescuerResp7")
                }
                h3 { attributes["data-i18n"] = "termsForAdoptersH3"; +"For Adopters:" }
                ul {
                    i18nLi("termsAdopterResp1")
                    i18nLi("termsAdopterResp2")
                    i18nLi("termsAdopterResp3")
                    i18nLi("termsAdopterResp4")
                }

                h2 { attributes["data-i18n"] = "termsPetListingsH2"; +"Pet Listings and Information" }
                p { attributes["data-i18n"] = "termsPetListingsText1"; +"Adopt-U does not verify, endorse, or guarantee the accuracy of information provided in pet listings. Information including but not limited to breed, age, health status, temperament, and vaccination records is provided by rescuers and has not been independently verified." }
                p { attributes["data-i18n"] = "termsPetListingsText2"; +"Photos and descriptions are provided by rescuers. While we prohibit deliberate misrepresentation, we cannot guarantee that all listings accurately represent the current condition of the animal." }
                p { attributes["data-i18n"] = "termsPetListingsText3"; +"Users are encouraged to ask questions, request veterinary records, and meet pets before finalizing any adoption arrangement." }

                h2 { attributes["data-i18n"] = "termsAdoptionProcessH2"; +"Adoption Process" }
                p { attributes["data-i18n"] = "termsAdoptionProcessIntro"; +"Once a rescuer and adopter agree to proceed with an adoption:" }
                ul {
                    i18nLi("termsAdoptionStep1")
                    i18nLi("termsAdoptionStep2")
                    i18nLi("termsAdoptionStep3")
                    i18nLi("termsAdoptionStep4")
                }

                h2 { attributes["data-i18n"] = "termsLiabilityH2"; +"Limitation of Liability" }
                p { attributes["data-i18n"] = "termsLiabilityIntro"; +"Adopt-U shall not be held liable for:" }
                ul {
                    i18nLi("termsLiability1")
                    i18nLi("termsLiability2")
                    i18nLi("termsLiability3")
                    i18nLi("termsLiability4")
                    i18nLi("termsLiability5")
                    i18nLi("termsLiability6")
                }
                p { attributes["data-i18n"] = "termsLiabilityAck"; +"Users acknowledge that adopting a pet is a significant commitment and that they assume full responsibility for their decision to adopt." }

                h2 { attributes["data-i18n"] = "termsProhibitedH2"; +"Prohibited Activities" }
                p { attributes["data-i18n"] = "termsProhibitedIntro"; +"The following activities are strictly prohibited on Adopt-U:" }
                ul {
                    i18nLi("termsProhibited1")
                    i18nLi("termsProhibited2")
                    i18nLi("termsProhibited3")
                    i18nLi("termsProhibited4")
                    i18nLi("termsProhibited5")
                    i18nLi("termsProhibited6")
                }

                h2 { attributes["data-i18n"] = "termsAccountTerminationH2"; +"Account Termination" }
                p { attributes["data-i18n"] = "termsAccountTerminationText"; +"Adopt-U reserves the right to suspend or terminate accounts that violate these terms, engage in prohibited activities, or bring the platform into disrepute. We may also remove listings that contain false, misleading, or inappropriate content." }

                h2 { attributes["data-i18n"] = "termsModificationsH2"; +"Modifications to Terms" }
                p { attributes["data-i18n"] = "termsModificationsText"; +"We reserve the right to modify these terms at any time. Continued use of the platform after changes constitutes acceptance of the modified terms. We will notify users of significant changes via email or platform announcements." }

                h2 { attributes["data-i18n"] = "privacyContactH2"; +"Contact Us" }
                p { attributes["data-i18n"] = "termsContactText"; +"If you have any questions about these Terms and Conditions, please contact us at admin@adopt-u.com." }
            }
        }
        footer()
        commonScripts()
    }
}
