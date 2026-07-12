package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

private fun UL.i18nLi(labelKey: String, descKey: String) {
    li { strong { attributes["data-i18n"] = labelKey; +labelText[labelKey]!! }; span { attributes["data-i18n"] = descKey; +labelText[descKey]!! } }
}

private fun UL.i18nLi(key: String) {
    li { attributes["data-i18n"] = key; +labelText[key]!! }
}

private val labelText: Map<String, String> = mapOf(
    "privacyLastUpdated" to "Last updated: April 2025",
    "privacyHighlight" to "At Adopt-U, we believe in minimal data collection. We only collect your email address to enable communication between adopters and rescuers. We do not use your information for advertising, and we will never share your data with third parties for marketing purposes.",
    "privacyInfoCollectedH2" to "Information We Collect",
    "privacyAccountRegH3" to "Account Registration",
    "privacyAccountRegIntro" to "To create an account on Adopt-U, we collect the following information:",
    "privacyEmailLabel" to "Email address: ",
    "privacyEmailDesc" to "Required for account verification, password recovery, and communication between adopters and rescuers",
    "privacyDisplayNameLabel" to "Display name: ",
    "privacyDisplayNameDesc" to "A name you choose to be identified by on the platform",
    "privacyLangPrefLabel" to "Language preference: ",
    "privacyLangPrefDesc" to "To provide the platform in your preferred language",
    "privacyNoFullNameEtc" to "We do not collect your full name, phone number, physical address, identification documents, payment information, or any other personal data beyond what is listed above.",
    "privacyOptionalProfileH3" to "Optional Profile Information",
    "privacyOptionalProfileIntro" to "Depending on your role, you may optionally provide:",
    "privacyAdopterLabel" to "As an Adopter: ",
    "privacyAdopterDesc" to "No additional information is required or collected",
    "privacyRescuerLabel" to "As a Rescuer: ",
    "privacyRescuerDesc" to "Location information for pets you have available for adoption",
    "privacyPhotographerLabel" to "As a Photographer: ",
    "privacyPhotographerDesc" to "Service location and contact preferences",
    "privacyTemporalHomeLabel" to "As a Temporal Home: ",
    "privacyTemporalHomeDesc" to "Location where you can provide temporary housing",
    "privacyLocationUsage" to "All location information is used solely to match adopters with nearby rescuers and service providers.",
    "privacyPetInfoH3" to "Pet Information",
    "privacyPetInfoIntro" to "Rescuers may post information about pets, including:",
    "privacyPetPhotos" to "Photos of the pet",
    "privacyPetDescBreedAge" to "Description, breed, age, and characteristics",
    "privacyPetHealth" to "Health and vaccination status",
    "privacyPetLocation" to "Location where the pet is available",
    "privacyPetInfoNotVerified" to "This information is provided by rescuers and is not independently verified by Adopt-U.",
    "privacyHowWeUseH2" to "How We Use Your Information",
    "privacyPrimaryPurposeH3" to "Primary Purpose: Facilitating Adoption Connections",
    "privacyEmailUsedFor" to "Your email address is used exclusively for the following purposes:",
    "privacyUseVerification" to "Sending you a verification link when you create an account",
    "privacyUseRescuerContact" to "Allowing rescuers to contact you about pets you're interested in",
    "privacyUseContactRescuer" to "Allowing you to contact rescuers about their listed pets",
    "privacyUseNotifications" to "Sending you important account and security notifications",
    "privacyOnceConnected" to "Once you connect with another user through our platform, all further communication is conducted directly between you and that party. We do not monitor, store, or have access to the content of your communications.",
    "privacyNeverDoH3" to "What We Will Never Do",
    "privacyCommitIntro" to "We solemnly commit to the following:",
    "privacyNoAdsLabel" to "No advertising: ",
    "privacyNoAdsDesc" to "We will never display targeted or non-targeted advertisements based on your activity",
    "privacyNoMarketingLabel" to "No marketing: ",
    "privacyNoMarketingDesc" to "We will never share your email with third parties for marketing or promotional purposes",
    "privacyNoSellingLabel" to "No data selling: ",
    "privacyNoSellingDesc" to "We will never sell, rent, or otherwise transfer your personal information to any third party",
    "privacyNoProfilingLabel" to "No profiling: ",
    "privacyNoProfilingDesc" to "We will not use your data to build profiles for any purpose beyond basic account management",
    "privacyNoTrackingLabel" to "No tracking: ",
    "privacyNoTrackingDesc" to "We do not use tracking pixels, cookies for advertising, or any similar tracking technologies",
    "privacyDataStorageH2" to "Data Storage and Security",
    "privacyDataStoredSecurely" to "Your data is stored securely using industry-standard encryption. We employ appropriate technical and organizational measures to protect your personal information against unauthorized access, alteration, disclosure, or destruction.",
    "privacyDataRetention" to "We retain your account information for as long as your account remains active. You may request deletion of your account and associated data at any time by contacting us.",
    "privacyYourRightsH2" to "Your Rights",
    "privacyRightsIntro" to "You have the following rights regarding your personal data:",
    "privacyRightAccess" to "Access: You may request a copy of all personal data we hold about you",
    "privacyRightCorrection" to "Correction: You may request that we correct any inaccurate information",
    "privacyRightDeletion" to "Deletion: You may request that we delete your account and all associated data",
    "privacyRightPortability" to "Portability: You may request that we provide your data in a commonly used format",
    "privacyExerciseRights" to "To exercise any of these rights, please contact us at admin@adopt-u.com.",
    "privacyCookiesH2" to "Cookies",
    "privacyCookiesText" to "Adopt-U uses minimal cookies necessary for platform functionality, including session management and language preferences. We do not use advertising cookies or tracking cookies.",
    "privacyThirdPartyH2" to "Third-Party Services",
    "privacyThirdPartyIntro" to "We use the following third-party services, which may process your data:",
    "privacyAws" to "AWS (Amazon Web Services): For data storage and email delivery. Their privacy policies apply to their processing of your data.",
    "privacyWebauthn" to "WebAuthn/FIDO2: For secure passkey authentication. This involves cryptographic operations in your browser.",
    "privacyThirdPartySelected" to "We have carefully selected service providers who share our commitment to data privacy.",
    "privacyChildrensH2" to "Children's Privacy",
    "privacyChildrensText" to "Adopt-U is not intended for users under the age of 18. We do not knowingly collect information from minors. If you believe a minor has created an account, please contact us to have it removed.",
    "privacyChangesH2" to "Changes to This Policy",
    "privacyChangesText" to "We may update this Privacy Policy from time to time to reflect changes in our practices or legal requirements. We will notify users of significant changes via email or prominent notice on the platform.",
    "privacyContactH2" to "Contact Us",
    "privacyContactText" to "If you have any questions or concerns about this Privacy Policy, or to exercise your data rights, please contact us at admin@adopt-u.com.",
    "privacyPolicy" to "Privacy Policy"
)

fun HTML.privacyPage(navParams: NavParams = NavParams()) {
    commonHead("Privacy Policy - Adopt-U", "policy.css")
    body {
        header {
            a("/") { commonLogo() }
            nav { commonNav(navParams.isLoggedIn, navParams.isAdmin, navParams.isRescuerOrAdmin, navParams.isTemporalHomeOrAdmin) }
        }
        main {
            div(classes = "policy-content") {
                h1 { attributes["data-i18n"] = "privacyPolicy"; +"Privacy Policy" }
                p { attributes["data-i18n"] = "privacyLastUpdated"; +"Last updated: April 2025" }

                p(classes = "privacy-highlight") {
                    attributes["data-i18n"] = "privacyHighlight"
                    +"At Adopt-U, we believe in minimal data collection. We only collect your email address to enable communication between adopters and rescuers. We do not use your information for advertising, and we will never share your data with third parties for marketing purposes."
                }

                h2 { attributes["data-i18n"] = "privacyInfoCollectedH2"; +"Information We Collect" }
                h3 { attributes["data-i18n"] = "privacyAccountRegH3"; +"Account Registration" }
                p { attributes["data-i18n"] = "privacyAccountRegIntro"; +"To create an account on Adopt-U, we collect the following information:" }
                ul {
                    i18nLi("privacyEmailLabel", "privacyEmailDesc")
                    i18nLi("privacyDisplayNameLabel", "privacyDisplayNameDesc")
                    i18nLi("privacyLangPrefLabel", "privacyLangPrefDesc")
                }
                p { attributes["data-i18n"] = "privacyNoFullNameEtc"; +"We do not collect your full name, phone number, physical address, identification documents, payment information, or any other personal data beyond what is listed above." }

                h3 { attributes["data-i18n"] = "privacyOptionalProfileH3"; +"Optional Profile Information" }
                p { attributes["data-i18n"] = "privacyOptionalProfileIntro"; +"Depending on your role, you may optionally provide:" }
                ul {
                    i18nLi("privacyAdopterLabel", "privacyAdopterDesc")
                    i18nLi("privacyRescuerLabel", "privacyRescuerDesc")
                    i18nLi("privacyPhotographerLabel", "privacyPhotographerDesc")
                    i18nLi("privacyTemporalHomeLabel", "privacyTemporalHomeDesc")
                }
                p { attributes["data-i18n"] = "privacyLocationUsage"; +"All location information is used solely to match adopters with nearby rescuers and service providers." }

                h3 { attributes["data-i18n"] = "privacyPetInfoH3"; +"Pet Information" }
                p { attributes["data-i18n"] = "privacyPetInfoIntro"; +"Rescuers may post information about pets, including:" }
                ul {
                    i18nLi("privacyPetPhotos")
                    i18nLi("privacyPetDescBreedAge")
                    i18nLi("privacyPetHealth")
                    i18nLi("privacyPetLocation")
                }
                p { attributes["data-i18n"] = "privacyPetInfoNotVerified"; +"This information is provided by rescuers and is not independently verified by Adopt-U." }

                h2 { attributes["data-i18n"] = "privacyHowWeUseH2"; +"How We Use Your Information" }
                h3 { attributes["data-i18n"] = "privacyPrimaryPurposeH3"; +"Primary Purpose: Facilitating Adoption Connections" }
                p { attributes["data-i18n"] = "privacyEmailUsedFor"; +"Your email address is used exclusively for the following purposes:" }
                ul {
                    i18nLi("privacyUseVerification")
                    i18nLi("privacyUseRescuerContact")
                    i18nLi("privacyUseContactRescuer")
                    i18nLi("privacyUseNotifications")
                }
                p { attributes["data-i18n"] = "privacyOnceConnected"; +"Once you connect with another user through our platform, all further communication is conducted directly between you and that party. We do not monitor, store, or have access to the content of your communications." }

                h3 { attributes["data-i18n"] = "privacyNeverDoH3"; +"What We Will Never Do" }
                p { attributes["data-i18n"] = "privacyCommitIntro"; +"We solemnly commit to the following:" }
                ul {
                    i18nLi("privacyNoAdsLabel", "privacyNoAdsDesc")
                    i18nLi("privacyNoMarketingLabel", "privacyNoMarketingDesc")
                    i18nLi("privacyNoSellingLabel", "privacyNoSellingDesc")
                    i18nLi("privacyNoProfilingLabel", "privacyNoProfilingDesc")
                    i18nLi("privacyNoTrackingLabel", "privacyNoTrackingDesc")
                }

                h2 { attributes["data-i18n"] = "privacyDataStorageH2"; +"Data Storage and Security" }
                p { attributes["data-i18n"] = "privacyDataStoredSecurely"; +"Your data is stored securely using industry-standard encryption. We employ appropriate technical and organizational measures to protect your personal information against unauthorized access, alteration, disclosure, or destruction." }
                p { attributes["data-i18n"] = "privacyDataRetention"; +"We retain your account information for as long as your account remains active. You may request deletion of your account and associated data at any time by contacting us." }

                h2 { attributes["data-i18n"] = "privacyYourRightsH2"; +"Your Rights" }
                p { attributes["data-i18n"] = "privacyRightsIntro"; +"You have the following rights regarding your personal data:" }
                ul {
                    i18nLi("privacyRightAccess")
                    i18nLi("privacyRightCorrection")
                    i18nLi("privacyRightDeletion")
                    i18nLi("privacyRightPortability")
                }
                p { attributes["data-i18n"] = "privacyExerciseRights"; +"To exercise any of these rights, please contact us at admin@adopt-u.com." }

                h2 { attributes["data-i18n"] = "privacyCookiesH2"; +"Cookies" }
                p { attributes["data-i18n"] = "privacyCookiesText"; +"Adopt-U uses minimal cookies necessary for platform functionality, including session management and language preferences. We do not use advertising cookies or tracking cookies." }

                h2 { attributes["data-i18n"] = "privacyThirdPartyH2"; +"Third-Party Services" }
                p { attributes["data-i18n"] = "privacyThirdPartyIntro"; +"We use the following third-party services, which may process your data:" }
                ul {
                    i18nLi("privacyAws")
                    i18nLi("privacyWebauthn")
                }
                p { attributes["data-i18n"] = "privacyThirdPartySelected"; +"We have carefully selected service providers who share our commitment to data privacy." }

                h2 { attributes["data-i18n"] = "privacyChildrensH2"; +"Children's Privacy" }
                p { attributes["data-i18n"] = "privacyChildrensText"; +"Adopt-U is not intended for users under the age of 18. We do not knowingly collect information from minors. If you believe a minor has created an account, please contact us to have it removed." }

                h2 { attributes["data-i18n"] = "privacyChangesH2"; +"Changes to This Policy" }
                p { attributes["data-i18n"] = "privacyChangesText"; +"We may update this Privacy Policy from time to time to reflect changes in our practices or legal requirements. We will notify users of significant changes via email or prominent notice on the platform." }

                h2 { attributes["data-i18n"] = "privacyContactH2"; +"Contact Us" }
                p { attributes["data-i18n"] = "privacyContactText"; +"If you have any questions or concerns about this Privacy Policy, or to exercise your data rights, please contact us at admin@adopt-u.com." }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
