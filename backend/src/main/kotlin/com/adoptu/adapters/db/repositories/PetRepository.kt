package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.AdoptionRequests
import com.adoptu.adapters.db.PetImages
import com.adoptu.adapters.db.Pets
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.common.Country
import com.adoptu.dto.input.AdoptionExperience
import com.adoptu.dto.input.AdoptionRequestDto
import com.adoptu.dto.input.Currency
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.HousingType
import com.adoptu.dto.input.PetDto
import com.adoptu.dto.input.PetImageDto
import com.adoptu.dto.input.PromotedReason
import com.adoptu.dto.input.Status
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.PagedResult
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.adapters.db.dbDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PetRepositoryImpl(private val clock: Clock) : PetRepositoryPort {

    private fun buildPetDto(row: ResultRow, images: List<PetImageDto>): PetDto {
        val petId = row[Pets.id]
        return PetDto(
            id = petId,
            rescuerId = row[Pets.rescuerId],
            name = row[Pets.name],
            type = row[Pets.type],
            breed = row[Pets.breed],
            description = row[Pets.description],
            weight = row[Pets.weight].toDouble(),
            ageYears = row[Pets.ageYears],
            ageMonths = row[Pets.ageMonths],
            status = Status.valueOf(row[Pets.status]),
            sex = Gender.valueOf(row[Pets.sex]),
            color = row[Pets.color],
            size = row[Pets.size],
            temperament = row[Pets.temperament],
            isSterilized = row[Pets.isSterilized],
            isMicrochipped = row[Pets.isMicrochipped],
            microchipId = row[Pets.microchipId],
            vaccinations = row[Pets.vaccinations],
            isGoodWithKids = row[Pets.isGoodWithKids],
            isGoodWithDogs = row[Pets.isGoodWithDogs],
            isGoodWithCats = row[Pets.isGoodWithCats],
            isHouseTrained = row[Pets.isHouseTrained],
            energyLevel = row[Pets.energyLevel],
            rescueDate = row[Pets.rescueDate],
            rescueLocation = row[Pets.rescueLocation],
            country = row[Pets.country]?.displayName,
            specialNeeds = row[Pets.specialNeeds],
            adoptionFee = row[Pets.adoptionFee].toDouble(),
            currency = Currency.valueOf(row[Pets.currency]),
            isUrgent = row[Pets.isUrgent],
            isPromoted = row[Pets.isPromoted],
            promotedReason = row[Pets.promotedReason]?.let { PromotedReason.valueOf(it) },
            promotedReasonDetail = row[Pets.promotedReasonDetail],
            createdAt = row[Pets.createdAt],
            deactivatedAt = row[Pets.deactivatedAt],
            deactivatedBy = row[Pets.deactivatedBy],
            images = images,
            videoUrl = row[Pets.videoUrl]
        )
    }

    private fun rowToPetDto(row: ResultRow): PetDto = buildPetDto(row, getPetImages(row[Pets.id]))

    private fun getPetImages(petId: Int): List<PetImageDto> = transaction {
        PetImages.selectAll()
            .where { PetImages.petId eq petId }
            .orderBy(PetImages.sortOrder, SortOrder.ASC)
            .map { row ->
                PetImageDto(
                    id = row[PetImages.id],
                    imageUrl = row[PetImages.imageUrl],
                    isPrimary = row[PetImages.isPrimary],
                    sortOrder = row[PetImages.sortOrder]
                )
            }
    }

    // Batches images for a whole listing page in a single query instead of one nested
    // transaction{} per pet (was: N+1 — every rowToPetDto() call re-queried pet_images).
    // See .wolf/cerebrum.md 2026-06-30: PetRepository.getAll was flagged as executing this
    // pattern sequentially for every row in a listing.
    private fun getImagesForPetIds(petIds: List<Int>): Map<Int, List<PetImageDto>> {
        if (petIds.isEmpty()) return emptyMap()
        return PetImages.selectAll()
            .where { PetImages.petId inList petIds }
            .orderBy(PetImages.sortOrder, SortOrder.ASC)
            .map { row ->
                row[PetImages.petId] to PetImageDto(
                    id = row[PetImages.id],
                    imageUrl = row[PetImages.imageUrl],
                    isPrimary = row[PetImages.isPrimary],
                    sortOrder = row[PetImages.sortOrder]
                )
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun rowsToPetDtos(rows: List<ResultRow>): List<PetDto> {
        val imagesByPetId = getImagesForPetIds(rows.map { it[Pets.id] })
        return rows.map { row -> buildPetDto(row, imagesByPetId[row[Pets.id]] ?: emptyList()) }
    }

    override suspend fun getAll(type: String?, showPromotedOnly: Boolean, country: String): List<PetDto> = withContext(dbDispatcher) {
        transaction {
            val parsedCountry = Country.fromDisplayName(country) ?: return@transaction emptyList()

            val baseCondition = (Pets.status eq "AVAILABLE") and (Pets.country eq parsedCountry) and Pets.deactivatedAt.isNull()
            val finalCondition = if (type != null) {
                if (showPromotedOnly) {
                    baseCondition and (Pets.type eq type.uppercase()) and (Pets.isPromoted eq true)
                } else {
                    baseCondition and (Pets.type eq type.uppercase())
                }
            } else {
                if (showPromotedOnly) {
                    baseCondition and (Pets.isPromoted eq true)
                } else {
                    baseCondition
                }
            }

            val rescuerIds = UserActiveRoles.select(UserActiveRoles.userId)
                .where { UserActiveRoles.role eq UserRole.RESCUER.name }
                .map { it[UserActiveRoles.userId] }

            // Promoted pets (free, needs-based "urgently needs a new home" flag - not a paid
            // boost, see PromotedReason) sort first; everything else stays newest-first.
            val rows = Pets.selectAll()
                .where { finalCondition and (Pets.rescuerId inList rescuerIds) }
                .orderBy(Pets.isPromoted, SortOrder.DESC)
                .orderBy(Pets.createdAt, SortOrder.DESC)
                .toList()
            rowsToPetDtos(rows)
        }
    }

    override suspend fun getAllUnfiltered(): List<PetDto> = withContext(dbDispatcher) {
        transaction {
            val rows = Pets.selectAll()
                .orderBy(Pets.createdAt, SortOrder.DESC)
                .toList()
            rowsToPetDtos(rows)
        }
    }

    override suspend fun getAllForAdmin(page: Int, pageSize: Int, search: String?, includeInactive: Boolean): PagedResult<PetDto> = withContext(dbDispatcher) {
        transaction {
            var condition: Op<Boolean> = Op.TRUE
            if (!includeInactive) condition = condition and Pets.deactivatedAt.isNull()
            if (!search.isNullOrBlank()) {
                condition = condition and (Pets.name.lowerCase() like "%${search.trim().lowercase()}%")
            }

            val total = Pets.selectAll().where { condition }.count().toInt()

            val safePage = page.coerceAtLeast(1)
            val safePageSize = pageSize.coerceIn(1, 100)
            val rows = Pets.selectAll()
                .where { condition }
                .orderBy(Pets.createdAt, SortOrder.DESC)
                .limit(safePageSize)
                .offset(((safePage - 1) * safePageSize).toLong())
                .toList()

            PagedResult(items = rowsToPetDtos(rows), total = total, page = safePage, pageSize = safePageSize)
        }
    }

    override suspend fun getById(id: Int): PetDto? = withContext(dbDispatcher) {
        transaction {
            Pets.selectAll().where { Pets.id eq id }.map(::rowToPetDto).firstOrNull()
        }
    }

    override suspend fun create(
        rescuerId: Int,
        name: String,
        type: String,
        description: String,
        weight: Double,
        ageYears: Int,
        ageMonths: Int,
        sex: Gender,
        breed: String?,
        color: String?,
        size: String?,
        temperament: String?,
        isSterilized: Boolean,
        isMicrochipped: Boolean,
        microchipId: String?,
        vaccinations: String?,
        isGoodWithKids: Boolean,
        isGoodWithDogs: Boolean,
        isGoodWithCats: Boolean,
        isHouseTrained: Boolean,
        energyLevel: String?,
        rescueDate: Long?,
        rescueLocation: String?,
        country: String?,
        specialNeeds: String?,
        adoptionFee: Double,
        currency: Currency,
        isUrgent: Boolean,
        isPromoted: Boolean,
        promotedReason: PromotedReason?,
        promotedReasonDetail: String?,
        status: String
    ): PetDto = withContext(dbDispatcher) {
        val parsedCountry = country?.let {
            Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
        }
        transaction {
        val id = Pets.insert {
            it[Pets.rescuerId] = rescuerId
            it[Pets.name] = name
            it[Pets.type] = type.uppercase()
            it[Pets.breed] = breed
            it[Pets.description] = description
            it[Pets.weight] = BigDecimal(weight.toString())
            it[Pets.ageYears] = ageYears
            it[Pets.ageMonths] = ageMonths
            it[Pets.status] = status
            it[Pets.sex] = sex.name
            it[Pets.color] = color
            it[Pets.size] = size
            it[Pets.temperament] = temperament
            it[Pets.isSterilized] = isSterilized
            it[Pets.isMicrochipped] = isMicrochipped
            it[Pets.microchipId] = microchipId
            it[Pets.vaccinations] = vaccinations
            it[Pets.isGoodWithKids] = isGoodWithKids
            it[Pets.isGoodWithDogs] = isGoodWithDogs
            it[Pets.isGoodWithCats] = isGoodWithCats
            it[Pets.isHouseTrained] = isHouseTrained
            it[Pets.energyLevel] = energyLevel
            it[Pets.rescueDate] = rescueDate
            it[Pets.rescueLocation] = rescueLocation
            it[Pets.country] = parsedCountry
            it[Pets.specialNeeds] = specialNeeds
            it[Pets.adoptionFee] = BigDecimal(adoptionFee.toString())
            it[Pets.currency] = currency.name
            it[Pets.isUrgent] = isUrgent
            it[Pets.isPromoted] = isPromoted
            it[Pets.promotedReason] = promotedReason?.name
            it[Pets.promotedReasonDetail] = promotedReasonDetail
            it[Pets.createdAt] = clock.now().toEpochMilliseconds()
        } get Pets.id

        Pets.selectAll().where { Pets.id eq id }.map(::rowToPetDto).first()
        }
    }

    override suspend fun update(id: Int, body: UpdatePetRequest): PetDto? = withContext(dbDispatcher) {
        transaction {
        Pets.update({ Pets.id eq id }) {
            body.name?.let { name -> it[Pets.name] = name }
            body.type?.let { type -> it[Pets.type] = type.uppercase() }
            body.breed?.let { breed -> it[Pets.breed] = breed }
            body.description?.let { desc -> it[Pets.description] = desc }
            body.weight?.let { w -> it[Pets.weight] = BigDecimal(w.toString()) }
            body.ageYears?.let { y -> it[Pets.ageYears] = y }
            body.ageMonths?.let { m -> it[Pets.ageMonths] = m }
            body.status?.let { s -> it[Pets.status] = s.name }
            body.sex?.let { s -> it[Pets.sex] = s.name }
            body.color?.let { c -> it[Pets.color] = c }
            body.size?.let { s -> it[Pets.size] = s }
            body.temperament?.let { t -> it[Pets.temperament] = t }
            body.isSterilized?.let { n -> it[Pets.isSterilized] = n }
            body.isMicrochipped?.let { m -> it[Pets.isMicrochipped] = m }
            body.microchipId?.let { m -> it[Pets.microchipId] = m }
            body.vaccinations?.let { v -> it[Pets.vaccinations] = v }
            body.isGoodWithKids?.let { k -> it[Pets.isGoodWithKids] = k }
            body.isGoodWithDogs?.let { d -> it[Pets.isGoodWithDogs] = d }
            body.isGoodWithCats?.let { c -> it[Pets.isGoodWithCats] = c }
            body.isHouseTrained?.let { h -> it[Pets.isHouseTrained] = h }
            body.energyLevel?.let { e -> it[Pets.energyLevel] = e }
            body.rescueDate?.let { r -> it[Pets.rescueDate] = r }
            body.rescueLocation?.let { l -> it[Pets.rescueLocation] = l }
            body.country?.let { c -> it[Pets.country] = Country.fromDisplayName(c) ?: throw IllegalArgumentException("Invalid country: $c") }
            body.specialNeeds?.let { s -> it[Pets.specialNeeds] = s }
            body.adoptionFee?.let { f -> it[Pets.adoptionFee] = BigDecimal(f.toString()) }
            body.currency?.let { c -> it[Pets.currency] = c.name }
            body.isUrgent?.let { u -> it[Pets.isUrgent] = u }
            body.isPromoted?.let { p ->
                it[Pets.isPromoted] = p
                if (!p) {
                    it[Pets.promotedReason] = null
                    it[Pets.promotedReasonDetail] = null
                }
            }
            body.promotedReason?.let { r -> it[Pets.promotedReason] = r.name }
            body.promotedReasonDetail?.let { d -> it[Pets.promotedReasonDetail] = d }
        }
        Pets.selectAll().where { Pets.id eq id }.map(::rowToPetDto).firstOrNull()
        }
    }

    override suspend fun delete(petId: Int): Unit = withContext(dbDispatcher) {
        transaction {
            exec("DELETE FROM pet_images WHERE pet_id = ?", listOf(IntegerColumnType() to petId))
            exec("DELETE FROM adoption_requests WHERE pet_id = ?", listOf(IntegerColumnType() to petId))
            exec("DELETE FROM pets WHERE id = ?", listOf(IntegerColumnType() to petId))
        }
    }

    override suspend fun deactivatePet(petId: Int, deactivatedBy: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            val rowsUpdated = Pets.update({ Pets.id eq petId }) {
                it[Pets.deactivatedAt] = clock.now().toEpochMilliseconds()
                it[Pets.deactivatedBy] = deactivatedBy
            }
            rowsUpdated > 0
        }
    }

    override suspend fun reactivatePet(petId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            val rowsUpdated = Pets.update({ Pets.id eq petId }) {
                it[Pets.deactivatedAt] = null
                it[Pets.deactivatedBy] = null
            }
            rowsUpdated > 0
        }
    }

    private fun rowToAdoptionRequestDto(row: ResultRow): AdoptionRequestDto = AdoptionRequestDto(
        id = row[AdoptionRequests.id],
        petId = row[AdoptionRequests.petId],
        adopterId = row[AdoptionRequests.adopterId],
        message = row[AdoptionRequests.message],
        status = row[AdoptionRequests.status],
        housingType = row[AdoptionRequests.housingType]?.let { HousingType.valueOf(it) },
        hasYard = row[AdoptionRequests.hasYard],
        hasOtherPets = row[AdoptionRequests.hasOtherPets],
        experienceLevel = row[AdoptionRequests.experienceLevel]?.let { AdoptionExperience.valueOf(it) },
        reviewNote = row[AdoptionRequests.reviewNote],
        createdAt = row[AdoptionRequests.createdAt]
    )

    override suspend fun createAdoptionRequest(
        petId: Int,
        adopterId: Int,
        message: String,
        housingType: HousingType?,
        hasYard: Boolean?,
        hasOtherPets: Boolean?,
        experienceLevel: AdoptionExperience?
    ): AdoptionRequestDto = withContext(dbDispatcher) {
        transaction {
        val createdAt = clock.now().toEpochMilliseconds()
        val id = AdoptionRequests.insert {
            it[AdoptionRequests.petId] = petId
            it[AdoptionRequests.adopterId] = adopterId
            it[AdoptionRequests.message] = message
            it[AdoptionRequests.status] = "PENDING"
            it[AdoptionRequests.housingType] = housingType?.name
            it[AdoptionRequests.hasYard] = hasYard
            it[AdoptionRequests.hasOtherPets] = hasOtherPets
            it[AdoptionRequests.experienceLevel] = experienceLevel?.name
            it[AdoptionRequests.createdAt] = createdAt
        } get AdoptionRequests.id

        AdoptionRequestDto(
            id = id,
            petId = petId,
            adopterId = adopterId,
            message = message,
            status = "PENDING",
            housingType = housingType,
            hasYard = hasYard,
            hasOtherPets = hasOtherPets,
            experienceLevel = experienceLevel,
            createdAt = createdAt
        )
        }
    }

    override suspend fun getAdoptionRequestsForPet(petId: Int): List<AdoptionRequestDto> = withContext(dbDispatcher) {
        transaction {
            AdoptionRequests.selectAll()
                .where { AdoptionRequests.petId eq petId }
                .map(::rowToAdoptionRequestDto)
        }
    }

    override suspend fun getAdoptionRequestsForUser(userId: Int): List<AdoptionRequestDto> = withContext(dbDispatcher) {
        transaction {
            AdoptionRequests.selectAll()
                .where { AdoptionRequests.adopterId eq userId }
                .map(::rowToAdoptionRequestDto)
        }
    }

    override suspend fun updateAdoptionRequestStatus(requestId: Int, status: String, reviewNote: String?): Boolean = withContext(dbDispatcher) {
        transaction {
            val updated = AdoptionRequests.update({ AdoptionRequests.id eq requestId }) {
                it[AdoptionRequests.status] = status
                reviewNote?.let { note -> it[AdoptionRequests.reviewNote] = note }
            }
            updated > 0
        }
    }

    override suspend fun getAdoptionRequestById(requestId: Int): AdoptionRequestDto? = withContext(dbDispatcher) {
        transaction {
            AdoptionRequests.selectAll()
                .where { AdoptionRequests.id eq requestId }
                .firstOrNull()
                ?.let(::rowToAdoptionRequestDto)
        }
    }

    override suspend fun addImage(petId: Int, imageUrl: String, isPrimary: Boolean, sortOrder: Int): PetImageDto = withContext(dbDispatcher) {
        transaction {
            if (isPrimary) {
                PetImages.update({ PetImages.petId eq petId }) {
                    it[PetImages.isPrimary] = false
                }
            }
            val maxOrder = PetImages.selectAll()
                .where { PetImages.petId eq petId }
                .orderBy(PetImages.sortOrder, SortOrder.DESC)
                .limit(1)
                .map { it[PetImages.sortOrder] }
                .firstOrNull() ?: -1

            val id = PetImages.insert {
                it[PetImages.petId] = petId
                it[PetImages.imageUrl] = imageUrl
                it[PetImages.isPrimary] = isPrimary
                it[PetImages.sortOrder] = if (sortOrder > 0) sortOrder else maxOrder + 1
            } get PetImages.id

            PetImageDto(
                id = id,
                imageUrl = imageUrl,
                isPrimary = isPrimary,
                sortOrder = if (sortOrder > 0) sortOrder else maxOrder + 1
            )
        }
    }

    override suspend fun removeImage(petId: Int, imageId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            val image = PetImages.selectAll()
                .where { (PetImages.id eq imageId) and (PetImages.petId eq petId) }
                .firstOrNull()
            if (image != null) {
                exec("DELETE FROM pet_images WHERE id = ?", listOf(IntegerColumnType() to imageId))
                true
            } else {
                false
            }
        }
    }

    override suspend fun setVideo(petId: Int, videoUrl: String?): PetDto? = withContext(dbDispatcher) {
        transaction {
            val updated = Pets.update({ Pets.id eq petId }) {
                it[Pets.videoUrl] = videoUrl
            }
            if (updated == 0) return@transaction null
            Pets.selectAll().where { Pets.id eq petId }.firstOrNull()?.let(::rowToPetDto)
        }
    }

    override suspend fun setPrimaryImage(petId: Int, imageId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            val image = PetImages.selectAll()
                .where { (PetImages.id eq imageId) and (PetImages.petId eq petId) }
                .firstOrNull()
            if (image != null) {
                PetImages.update({ PetImages.petId eq petId }) {
                    it[PetImages.isPrimary] = false
                }
                PetImages.update({ PetImages.id eq imageId }) {
                    it[PetImages.isPrimary] = true
                }
                true
            } else {
                false
            }
        }
    }

    override suspend fun getImages(petId: Int): List<PetImageDto> = withContext(dbDispatcher) {
        getPetImages(petId)
    }
}
