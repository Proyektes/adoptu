package com.adoptu.pages

import com.adoptu.routes.NavParams
import kotlinx.html.*

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
                p { +"Last updated: April 2025" }
                
                p(classes = "privacy-highlight") {
                    +"At Adopt-U, we believe in minimal data collection. We only collect your email address to enable communication between adopters and rescuers. We do not use your information for advertising, and we will never share your data with third parties for marketing purposes."
                }
                
                h2 { +"Information We Collect" }
                h3 { +"Account Registration" }
                p { +"To create an account on Adopt-U, we collect the following information:" }
                ul {
                    li { strong { +"Email address: " }; +"Required for account verification, password recovery, and communication between adopters and rescuers" }
                    li { strong { +"Display name: " }; +"A name you choose to be identified by on the platform" }
                    li { strong { +"Language preference: " }; +"To provide the platform in your preferred language" }
                }
                p { +"We do not collect your full name, phone number, physical address, identification documents, payment information, or any other personal data beyond what is listed above." }
                
                h3 { +"Optional Profile Information" }
                p { +"Depending on your role, you may optionally provide:" }
                ul {
                    li { strong { +"As an Adopter: " }; +"No additional information is required or collected" }
                    li { strong { +"As a Rescuer: " }; +"Location information for pets you have available for adoption" }
                    li { strong { +"As a Photographer: " }; +"Service location and contact preferences" }
                    li { strong { +"As a Temporal Home: " }; +"Location where you can provide temporary housing" }
                }
                p { +"All location information is used solely to match adopters with nearby rescuers and service providers." }
                
                h3 { +"Pet Information" }
                p { +"Rescuers may post information about pets, including:" }
                ul {
                    li { +"Photos of the pet" }
                    li { +"Description, breed, age, and characteristics" }
                    li { +"Health and vaccination status" }
                    li { +"Location where the pet is available" }
                }
                p { +"This information is provided by rescuers and is not independently verified by Adopt-U." }
                
                h2 { +"How We Use Your Information" }
                h3 { +"Primary Purpose: Facilitating Adoption Connections" }
                p { +"Your email address is used exclusively for the following purposes:" }
                ul {
                    li { +"Sending you a verification link when you create an account" }
                    li { +"Allowing rescuers to contact you about pets you're interested in" }
                    li { +"Allowing you to contact rescuers about their listed pets" }
                    li { +"Sending you important account and security notifications" }
                }
                p { +"Once you connect with another user through our platform, all further communication is conducted directly between you and that party. We do not monitor, store, or have access to the content of your communications." }
                
                h3 { +"What We Will Never Do" }
                p { +"We solemnly commit to the following:" }
                ul {
                    li { strong { +"No advertising: " }; +"We will never display targeted or non-targeted advertisements based on your activity" }
                    li { strong { +"No marketing: " }; +"We will never share your email with third parties for marketing or promotional purposes" }
                    li { strong { +"No data selling: " }; +"We will never sell, rent, or otherwise transfer your personal information to any third party" }
                    li { strong { +"No profiling: " }; +"We will not use your data to build profiles for any purpose beyond basic account management" }
                    li { strong { +"No tracking: " }; +"We do not use tracking pixels, cookies for advertising, or any similar tracking technologies" }
                }
                
                h2 { +"Data Storage and Security" }
                p { +"Your data is stored securely using industry-standard encryption. We employ appropriate technical and organizational measures to protect your personal information against unauthorized access, alteration, disclosure, or destruction." }
                p { +"We retain your account information for as long as your account remains active. You may request deletion of your account and associated data at any time by contacting us." }
                
                h2 { +"Your Rights" }
                p { +"You have the following rights regarding your personal data:" }
                ul {
                    li { +"Access: You may request a copy of all personal data we hold about you" }
                    li { +"Correction: You may request that we correct any inaccurate information" }
                    li { +"Deletion: You may request that we delete your account and all associated data" }
                    li { +"Portability: You may request that we provide your data in a commonly used format" }
                }
                p { +"To exercise any of these rights, please contact us at admin@adopt-u.com." }
                
                h2 { +"Cookies" }
                p { +"Adopt-U uses minimal cookies necessary for platform functionality, including session management and language preferences. We do not use advertising cookies or tracking cookies." }
                
                h2 { +"Third-Party Services" }
                p { +"We use the following third-party services, which may process your data:" }
                ul {
                    li { +"AWS (Amazon Web Services): For data storage and email delivery. Their privacy policies apply to their processing of your data." }
                    li { +"WebAuthn/FIDO2: For secure passkey authentication. This involves cryptographic operations in your browser." }
                }
                p { +"We have carefully selected service providers who share our commitment to data privacy." }
                
                h2 { +"Children's Privacy" }
                p { +"Adopt-U is not intended for users under the age of 18. We do not knowingly collect information from minors. If you believe a minor has created an account, please contact us to have it removed." }
                
                h2 { +"Changes to This Policy" }
                p { +"We may update this Privacy Policy from time to time to reflect changes in our practices or legal requirements. We will notify users of significant changes via email or prominent notice on the platform." }
                
                h2 { +"Contact Us" }
                p { +"If you have any questions or concerns about this Privacy Policy, or to exercise your data rights, please contact us at admin@adopt-u.com." }
            }
        }
        footer()
        commonScripts(navParams.isLoggedIn)
    }
}
