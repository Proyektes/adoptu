package com.adoptu.dto.input


data class AssertionOptionsDto(
    val challenge: String,
    val rpId: String
)



data class RelyingPartyDto(val id: String, val name: String)

data class WebAuthnUserIdentityDto(
    val id: String,
    val name: String,
    val displayName: String
)

data class PubKeyCredParamDto(
    val type: String,
    val alg: Int
)

data class RegistrationOptionsDto(
    val rp: RelyingPartyDto,
    val user: WebAuthnUserIdentityDto,
    val challenge: String,
    val pubKeyCredParams: List<PubKeyCredParamDto>
)