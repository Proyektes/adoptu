package com.adoptu.web

import com.adoptu.dto.input.AcceptTermsRequest
import com.adoptu.dto.input.AdoptionRequestDto
import com.adoptu.dto.input.BanUserRequest
import com.adoptu.dto.input.BlockRescuerRequest
import com.adoptu.dto.input.ContactLostFoundReporterRequest
import com.adoptu.dto.input.CreateAdoptionRequestRequest
import com.adoptu.dto.input.CreateFosterPlacementRequest
import com.adoptu.dto.input.CreateMultiPhotographerRequestRequest
import com.adoptu.dto.input.CreatePetEditSuggestionRequest
import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.CreatePetRequest
import com.adoptu.dto.input.CreatePhotographyRequestRequest
import com.adoptu.dto.input.CreateSavedSearchRequest
import com.adoptu.dto.input.CreateShelterRequest
import com.adoptu.dto.input.CreateSponsorshipOfferRequest
import com.adoptu.dto.input.CreateSterilizationLocationRequest
import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.CreateUrgentRescuerProfileRequest
import com.adoptu.dto.input.CreateUserShelterRequest
import com.adoptu.dto.input.CreateUserSterilizationLocationRequest
import com.adoptu.dto.input.CreateVolunteerApplicationRequest
import com.adoptu.dto.input.LostFoundReportDto
import com.adoptu.dto.input.PetAnalyticsDto
import com.adoptu.dto.input.PetDto
import com.adoptu.dto.input.PetEditSuggestionDto
import com.adoptu.dto.input.PetFosterPlacementDto
import com.adoptu.dto.input.PetImageDto
import com.adoptu.dto.input.PetMedicalEventDto
import com.adoptu.dto.input.PhotographerDto
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.PhotographyRequestDto
import com.adoptu.dto.input.RescuerDetailDto
import com.adoptu.dto.input.RescuerDirectoryDto
import com.adoptu.dto.input.RescuerMedicalEventDto
import com.adoptu.dto.input.RoleActivationRequest
import com.adoptu.dto.input.SavedSearchDto
import com.adoptu.dto.input.SendTemporalHomeRequestRequest
import com.adoptu.dto.input.ShelterDto
import com.adoptu.dto.input.SponsorshipOfferDto
import com.adoptu.dto.input.SterilizationLocationDto
import com.adoptu.dto.input.SterilizationLocationsByCity
import com.adoptu.dto.input.SterilizationLocationsByLocation
import com.adoptu.dto.input.SterilizationLocationsByState
import com.adoptu.dto.input.SubmitLostFoundReportRequest
import com.adoptu.dto.input.SubmitUrgentReportRequest
import com.adoptu.dto.input.TemporalHomeDto
import com.adoptu.dto.input.TemporalHomeRequestDto
import com.adoptu.dto.input.TemporalHomeSearchParams
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.dto.input.UpdatePhotographyRequestRequest
import com.adoptu.dto.input.UpdateShelterRequest
import com.adoptu.dto.input.UpdateSterilizationLocationRequest
import com.adoptu.dto.input.UpdateTemporalHomeRequest
import com.adoptu.dto.input.UpdateUrgentRescuerProfileRequest
import com.adoptu.dto.input.UpdateUserShelterRequest
import com.adoptu.dto.input.UpdateUserSterilizationLocationRequest
import com.adoptu.dto.input.UpdateVolunteerStatusRequest
import com.adoptu.dto.input.UrgentReportDto
import com.adoptu.dto.input.UrgentReportPageDto
import com.adoptu.dto.input.UrgentRescuerLeaderboardEntryDto
import com.adoptu.dto.input.UrgentRescuerProfileDto
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserShelterDto
import com.adoptu.dto.input.UserSterilizationLocationDto
import com.adoptu.dto.input.VolunteerDto
import com.adoptu.dto.output.AuthMeResponse
import com.adoptu.dto.output.RegistrationResponse
import com.adoptu.dto.output.SuccessWithErrorResponse
import com.adoptu.dto.output.VerificationResponse
import com.universaliun.formats.helidon.DataFormatsKitMediaSupport
import com.universaliun.formats.helidon.JsonCodec
import com.universaliun.formats.json.generated.decodeAsAcceptTermsRequest
import com.universaliun.formats.json.generated.decodeAsAdoptionRequestDto
import com.universaliun.formats.json.generated.decodeAsAuthMeResponse
import com.universaliun.formats.json.generated.decodeAsBanUserRequest
import com.universaliun.formats.json.generated.decodeAsBlockRescuerRequest
import com.universaliun.formats.json.generated.decodeAsContactLostFoundReporterRequest
import com.universaliun.formats.json.generated.decodeAsCreateAdoptionRequestRequest
import com.universaliun.formats.json.generated.decodeAsCreateFosterPlacementRequest
import com.universaliun.formats.json.generated.decodeAsCreateMultiPhotographerRequestRequest
import com.universaliun.formats.json.generated.decodeAsCreatePetEditSuggestionRequest
import com.universaliun.formats.json.generated.decodeAsCreatePetMedicalEventRequest
import com.universaliun.formats.json.generated.decodeAsCreatePetRequest
import com.universaliun.formats.json.generated.decodeAsCreatePhotographyRequestRequest
import com.universaliun.formats.json.generated.decodeAsCreateSavedSearchRequest
import com.universaliun.formats.json.generated.decodeAsCreateShelterRequest
import com.universaliun.formats.json.generated.decodeAsCreateSponsorshipOfferRequest
import com.universaliun.formats.json.generated.decodeAsCreateSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsCreateTemporalHomeRequest
import com.universaliun.formats.json.generated.decodeAsCreateUrgentRescuerProfileRequest
import com.universaliun.formats.json.generated.decodeAsCreateUserShelterRequest
import com.universaliun.formats.json.generated.decodeAsCreateUserSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsCreateVolunteerApplicationRequest
import com.universaliun.formats.json.generated.decodeAsErrorResponse
import com.universaliun.formats.json.generated.decodeAsLostFoundReportDto
import com.universaliun.formats.json.generated.decodeAsPetAnalyticsDto
import com.universaliun.formats.json.generated.decodeAsPetDto
import com.universaliun.formats.json.generated.decodeAsPetEditSuggestionDto
import com.universaliun.formats.json.generated.decodeAsPetFosterPlacementDto
import com.universaliun.formats.json.generated.decodeAsPetImageDto
import com.universaliun.formats.json.generated.decodeAsPetMedicalEventDto
import com.universaliun.formats.json.generated.decodeAsPhotographerDto
import com.universaliun.formats.json.generated.decodeAsPhotographerSettingsRequest
import com.universaliun.formats.json.generated.decodeAsPhotographyRequestDto
import com.universaliun.formats.json.generated.decodeAsRegistrationResponse
import com.universaliun.formats.json.generated.decodeAsRescuerDetailDto
import com.universaliun.formats.json.generated.decodeAsRescuerDirectoryDto
import com.universaliun.formats.json.generated.decodeAsRescuerMedicalEventDto
import com.universaliun.formats.json.generated.decodeAsRoleActivationRequest
import com.universaliun.formats.json.generated.decodeAsSavedSearchDto
import com.universaliun.formats.json.generated.decodeAsSendTemporalHomeRequestRequest
import com.universaliun.formats.json.generated.decodeAsShelterDto
import com.universaliun.formats.json.generated.decodeAsSponsorshipOfferDto
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationDto
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationsByCity
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationsByLocation
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationsByState
import com.universaliun.formats.json.generated.decodeAsSubmitLostFoundReportRequest
import com.universaliun.formats.json.generated.decodeAsSubmitUrgentReportRequest
import com.universaliun.formats.json.generated.decodeAsSuccessResponse
import com.universaliun.formats.json.generated.decodeAsSuccessWithErrorResponse
import com.universaliun.formats.json.generated.decodeAsTemporalHomeDto
import com.universaliun.formats.json.generated.decodeAsTemporalHomeRequestDto
import com.universaliun.formats.json.generated.decodeAsTemporalHomeSearchParams
import com.universaliun.formats.json.generated.decodeAsUpdatePetRequest
import com.universaliun.formats.json.generated.decodeAsUpdatePhotographyRequestRequest
import com.universaliun.formats.json.generated.decodeAsUpdateShelterRequest
import com.universaliun.formats.json.generated.decodeAsUpdateSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsUpdateTemporalHomeRequest
import com.universaliun.formats.json.generated.decodeAsUpdateUrgentRescuerProfileRequest
import com.universaliun.formats.json.generated.decodeAsUpdateUserShelterRequest
import com.universaliun.formats.json.generated.decodeAsUpdateUserSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsUpdateVolunteerStatusRequest
import com.universaliun.formats.json.generated.decodeAsUrgentReportDto
import com.universaliun.formats.json.generated.decodeAsUrgentReportPageDto
import com.universaliun.formats.json.generated.decodeAsUrgentRescuerLeaderboardEntryDto
import com.universaliun.formats.json.generated.decodeAsUrgentRescuerProfileDto
import com.universaliun.formats.json.generated.decodeAsUserDto
import com.universaliun.formats.json.generated.decodeAsUserShelterDto
import com.universaliun.formats.json.generated.decodeAsUserSterilizationLocationDto
import com.universaliun.formats.json.generated.decodeAsVerificationResponse
import com.universaliun.formats.json.generated.decodeAsVolunteerDto
import com.universaliun.formats.json.generated.encodeToJson
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.helidon.common.config.Config
import io.helidon.http.media.MediaContext
import io.helidon.http.media.jackson.JacksonSupport
import io.helidon.http.media.multipart.MultiPartSupport

/**
 * Jackson's DefaultPrettyPrinter differs from kotlinx.serialization's prettyPrint - what every
 * response body was shaped like before this migration, and what ported test assertions still
 * expect - in two ways: (1) it renders empty arrays/objects as "[ ]"/"{ }" instead of "[]"/"{}",
 * and (2) its default field separator is " : " (space on both sides) instead of ": " (space
 * after only). Fix both; non-empty container indentation is otherwise unaffected.
 */
private class CompactEmptyContainerPrettyPrinter :
    DefaultPrettyPrinter(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER)) {

    override fun createInstance() = CompactEmptyContainerPrettyPrinter()

    override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
        if (nrOfValues > 0) {
            super.writeEndArray(g, nrOfValues)
        } else {
            g.writeRaw(']')
        }
    }

    override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
        if (nrOfEntries > 0) {
            super.writeEndObject(g, nrOfEntries)
        } else {
            g.writeRaw('}')
        }
    }
}

/**
 * Registers Helidon's request/response media support. DataFormatsKit-generated codecs
 * (`@JsonDecodable`/`@JsonEncodable`, see `dfk-codegen/`) handle every DTO under `dto/` plus
 * `web/Responses.kt`'s `ErrorResponse`/`SuccessResponse` - each is registered by exact class
 * below, since `DataFormatsKitMediaSupport` deliberately does no reflective POJO binding (see
 * DataFormatsKit's docs/helidon.md).
 *
 * Jackson stays registered too, as the fallback `MediaSupport` for everything NOT registered
 * above: `PagedResult<T>` (a generic wrapper class - `@JsonDecodable`/`@JsonEncodable` do not
 * support generic type parameters on the annotated class itself, and there are only two concrete
 * instantiations on the wire, `PagedResult<PetDto>`/`PagedResult<UserDto>`, both admin list
 * endpoints - see PagedResult.kt), and a handful of request DTOs declared directly in
 * `routes/AuthRoutes.kt` and `routes/UsersRoutes.kt` (outside this migration's `{dto,web}` scope;
 * they keep working unchanged via this fallback). `DataFormatsKitMediaSupport` reports
 * `NOT_SUPPORTED` for any type it wasn't told about, so Helidon's `MediaContext` falls through to
 * Jackson for all of these automatically - no per-route branching needed.
 *
 * `MultiPartSupport` is unchanged: image/video upload endpoints use
 * `ServerRequest.receiveMultipart()` (see RequestExtensions.kt), never JSON.
 */
object JsonSupport {
    val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setDefaultPrettyPrinter(CompactEmptyContainerPrettyPrinter())

    fun mediaContext(): MediaContext = MediaContext.builder()
        .addMediaSupport(
            DataFormatsKitMediaSupport.builder()
                .register(PetDto::class.java, JsonCodec.of({ it.decodeAsPetDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PetImageDto::class.java, JsonCodec.of({ it.decodeAsPetImageDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreatePetRequest::class.java, JsonCodec.of({ it.decodeAsCreatePetRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdatePetRequest::class.java, JsonCodec.of({ it.decodeAsUpdatePetRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UserDto::class.java, JsonCodec.of({ it.decodeAsUserDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(BanUserRequest::class.java, JsonCodec.of({ it.decodeAsBanUserRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PhotographerDto::class.java, JsonCodec.of({ it.decodeAsPhotographerDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(AcceptTermsRequest::class.java, JsonCodec.of({ it.decodeAsAcceptTermsRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PhotographerSettingsRequest::class.java, JsonCodec.of({ it.decodeAsPhotographerSettingsRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PhotographyRequestDto::class.java, JsonCodec.of({ it.decodeAsPhotographyRequestDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreatePhotographyRequestRequest::class.java, JsonCodec.of({ it.decodeAsCreatePhotographyRequestRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdatePhotographyRequestRequest::class.java, JsonCodec.of({ it.decodeAsUpdatePhotographyRequestRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateMultiPhotographerRequestRequest::class.java, JsonCodec.of({ it.decodeAsCreateMultiPhotographerRequestRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(RoleActivationRequest::class.java, JsonCodec.of({ it.decodeAsRoleActivationRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(TemporalHomeDto::class.java, JsonCodec.of({ it.decodeAsTemporalHomeDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(TemporalHomeSearchParams::class.java, JsonCodec.of({ it.decodeAsTemporalHomeSearchParams() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateTemporalHomeRequest::class.java, JsonCodec.of({ it.decodeAsCreateTemporalHomeRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdateTemporalHomeRequest::class.java, JsonCodec.of({ it.decodeAsUpdateTemporalHomeRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SendTemporalHomeRequestRequest::class.java, JsonCodec.of({ it.decodeAsSendTemporalHomeRequestRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(BlockRescuerRequest::class.java, JsonCodec.of({ it.decodeAsBlockRescuerRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(TemporalHomeRequestDto::class.java, JsonCodec.of({ it.decodeAsTemporalHomeRequestDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(AdoptionRequestDto::class.java, JsonCodec.of({ it.decodeAsAdoptionRequestDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateAdoptionRequestRequest::class.java, JsonCodec.of({ it.decodeAsCreateAdoptionRequestRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(LostFoundReportDto::class.java, JsonCodec.of({ it.decodeAsLostFoundReportDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SubmitLostFoundReportRequest::class.java, JsonCodec.of({ it.decodeAsSubmitLostFoundReportRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(ContactLostFoundReporterRequest::class.java, JsonCodec.of({ it.decodeAsContactLostFoundReporterRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PetAnalyticsDto::class.java, JsonCodec.of({ it.decodeAsPetAnalyticsDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PetEditSuggestionDto::class.java, JsonCodec.of({ it.decodeAsPetEditSuggestionDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreatePetEditSuggestionRequest::class.java, JsonCodec.of({ it.decodeAsCreatePetEditSuggestionRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PetFosterPlacementDto::class.java, JsonCodec.of({ it.decodeAsPetFosterPlacementDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateFosterPlacementRequest::class.java, JsonCodec.of({ it.decodeAsCreateFosterPlacementRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(PetMedicalEventDto::class.java, JsonCodec.of({ it.decodeAsPetMedicalEventDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreatePetMedicalEventRequest::class.java, JsonCodec.of({ it.decodeAsCreatePetMedicalEventRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(RescuerMedicalEventDto::class.java, JsonCodec.of({ it.decodeAsRescuerMedicalEventDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(RescuerDirectoryDto::class.java, JsonCodec.of({ it.decodeAsRescuerDirectoryDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(RescuerDetailDto::class.java, JsonCodec.of({ it.decodeAsRescuerDetailDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SavedSearchDto::class.java, JsonCodec.of({ it.decodeAsSavedSearchDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateSavedSearchRequest::class.java, JsonCodec.of({ it.decodeAsCreateSavedSearchRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(ShelterDto::class.java, JsonCodec.of({ it.decodeAsShelterDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateShelterRequest::class.java, JsonCodec.of({ it.decodeAsCreateShelterRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdateShelterRequest::class.java, JsonCodec.of({ it.decodeAsUpdateShelterRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UserShelterDto::class.java, JsonCodec.of({ it.decodeAsUserShelterDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateUserShelterRequest::class.java, JsonCodec.of({ it.decodeAsCreateUserShelterRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdateUserShelterRequest::class.java, JsonCodec.of({ it.decodeAsUpdateUserShelterRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SponsorshipOfferDto::class.java, JsonCodec.of({ it.decodeAsSponsorshipOfferDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateSponsorshipOfferRequest::class.java, JsonCodec.of({ it.decodeAsCreateSponsorshipOfferRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SterilizationLocationDto::class.java, JsonCodec.of({ it.decodeAsSterilizationLocationDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateSterilizationLocationRequest::class.java, JsonCodec.of({ it.decodeAsCreateSterilizationLocationRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdateSterilizationLocationRequest::class.java, JsonCodec.of({ it.decodeAsUpdateSterilizationLocationRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SterilizationLocationsByLocation::class.java, JsonCodec.of({ it.decodeAsSterilizationLocationsByLocation() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SterilizationLocationsByState::class.java, JsonCodec.of({ it.decodeAsSterilizationLocationsByState() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SterilizationLocationsByCity::class.java, JsonCodec.of({ it.decodeAsSterilizationLocationsByCity() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UserSterilizationLocationDto::class.java, JsonCodec.of({ it.decodeAsUserSterilizationLocationDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateUserSterilizationLocationRequest::class.java, JsonCodec.of({ it.decodeAsCreateUserSterilizationLocationRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdateUserSterilizationLocationRequest::class.java, JsonCodec.of({ it.decodeAsUpdateUserSterilizationLocationRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UrgentRescuerProfileDto::class.java, JsonCodec.of({ it.decodeAsUrgentRescuerProfileDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateUrgentRescuerProfileRequest::class.java, JsonCodec.of({ it.decodeAsCreateUrgentRescuerProfileRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdateUrgentRescuerProfileRequest::class.java, JsonCodec.of({ it.decodeAsUpdateUrgentRescuerProfileRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UrgentReportDto::class.java, JsonCodec.of({ it.decodeAsUrgentReportDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SubmitUrgentReportRequest::class.java, JsonCodec.of({ it.decodeAsSubmitUrgentReportRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UrgentReportPageDto::class.java, JsonCodec.of({ it.decodeAsUrgentReportPageDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UrgentRescuerLeaderboardEntryDto::class.java, JsonCodec.of({ it.decodeAsUrgentRescuerLeaderboardEntryDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(VolunteerDto::class.java, JsonCodec.of({ it.decodeAsVolunteerDto() }, { v, sink -> v.encodeToJson(sink) }))
                .register(CreateVolunteerApplicationRequest::class.java, JsonCodec.of({ it.decodeAsCreateVolunteerApplicationRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(UpdateVolunteerStatusRequest::class.java, JsonCodec.of({ it.decodeAsUpdateVolunteerStatusRequest() }, { v, sink -> v.encodeToJson(sink) }))
                .register(AuthMeResponse::class.java, JsonCodec.of({ it.decodeAsAuthMeResponse() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SuccessWithErrorResponse::class.java, JsonCodec.of({ it.decodeAsSuccessWithErrorResponse() }, { v, sink -> v.encodeToJson(sink) }))
                .register(RegistrationResponse::class.java, JsonCodec.of({ it.decodeAsRegistrationResponse() }, { v, sink -> v.encodeToJson(sink) }))
                .register(VerificationResponse::class.java, JsonCodec.of({ it.decodeAsVerificationResponse() }, { v, sink -> v.encodeToJson(sink) }))
                .register(ErrorResponse::class.java, JsonCodec.of({ it.decodeAsErrorResponse() }, { v, sink -> v.encodeToJson(sink) }))
                .register(SuccessResponse::class.java, JsonCodec.of({ it.decodeAsSuccessResponse() }, { v, sink -> v.encodeToJson(sink) }))
                .build(),
        )
        // Fallback for everything DataFormatsKitMediaSupport reports NOT_SUPPORTED for:
        // PagedResult<T> (generic wrapper, see PagedResult.kt) and the few request DTOs declared
        // directly in routes/AuthRoutes.kt + routes/UsersRoutes.kt (outside this migration's
        // {dto,web} scope).
        .addMediaSupport(JacksonSupport.create(objectMapper))
        .addMediaSupport(MultiPartSupport.create(Config.empty()))
        .build()
}
