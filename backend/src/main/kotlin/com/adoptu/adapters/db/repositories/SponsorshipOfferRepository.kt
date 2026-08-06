package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.SponsorshipOffers
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.dto.input.CreateSponsorshipOfferRequest
import com.adoptu.dto.input.Currency
import com.adoptu.dto.input.SponsorshipOfferDto
import com.adoptu.dto.input.SponsorshipOfferType
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.SponsorshipOfferRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Raw row + a denormalize() pass afterward - same shape as VolunteerRepositoryImpl/
// PetFosterPlacementRepositoryImpl, since resolving display names is a suspend call that can't
// run inside the transaction {} lambda.
private data class RawOffer(
    val id: Int,
    val sponsorId: Int,
    val rescuerId: Int,
    val petId: Int?,
    val offerType: SponsorshipOfferType,
    val amount: Double?,
    val currency: Currency?,
    val inKindDescription: String?,
    val message: String,
    val status: String,
    val createdAt: Long
)

@OptIn(ExperimentalTime::class)
class SponsorshipOfferRepositoryImpl(
    private val userRepository: UserRepositoryPort,
    private val petRepository: PetRepositoryPort,
    private val clock: Clock
) : SponsorshipOfferRepositoryPort {

    private fun rowToRaw(row: ResultRow) = RawOffer(
        id = row[SponsorshipOffers.id],
        sponsorId = row[SponsorshipOffers.sponsorId],
        rescuerId = row[SponsorshipOffers.rescuerId],
        petId = row[SponsorshipOffers.petId],
        offerType = SponsorshipOfferType.valueOf(row[SponsorshipOffers.offerType]),
        amount = row[SponsorshipOffers.amount]?.toDouble(),
        currency = row[SponsorshipOffers.currency]?.let { Currency.valueOf(it) },
        inKindDescription = row[SponsorshipOffers.inKindDescription],
        message = row[SponsorshipOffers.message],
        status = row[SponsorshipOffers.status],
        createdAt = row[SponsorshipOffers.createdAt]
    )

    private suspend fun denormalize(raw: RawOffer): SponsorshipOfferDto {
        val sponsor = userRepository.getById(raw.sponsorId)
        val rescuer = userRepository.getById(raw.rescuerId)
        val pet = raw.petId?.let { petRepository.getById(it) }
        return SponsorshipOfferDto(
            id = raw.id,
            sponsorId = raw.sponsorId,
            sponsorName = sponsor?.displayName,
            rescuerId = raw.rescuerId,
            rescuerName = rescuer?.displayName,
            petId = raw.petId,
            petName = pet?.name,
            offerType = raw.offerType,
            amount = raw.amount,
            currency = raw.currency,
            inKindDescription = raw.inKindDescription,
            message = raw.message,
            status = raw.status,
            createdAt = raw.createdAt
        )
    }

    override suspend fun create(sponsorId: Int, request: CreateSponsorshipOfferRequest): SponsorshipOfferDto {
        val now = clock.now().toEpochMilliseconds()
        val raw = withContext(dbDispatcher) {
            transaction {
                val id = SponsorshipOffers.insert {
                    it[SponsorshipOffers.sponsorId] = sponsorId
                    it[SponsorshipOffers.rescuerId] = request.rescuerId
                    it[SponsorshipOffers.petId] = request.petId
                    it[SponsorshipOffers.offerType] = request.offerType.name
                    it[SponsorshipOffers.amount] = request.amount?.let { a -> BigDecimal(a.toString()) }
                    it[SponsorshipOffers.currency] = request.currency?.name
                    it[SponsorshipOffers.inKindDescription] = request.inKindDescription
                    it[SponsorshipOffers.message] = request.message
                    it[SponsorshipOffers.status] = "SENT"
                    it[SponsorshipOffers.createdAt] = now
                } get SponsorshipOffers.id
                RawOffer(
                    id, sponsorId, request.rescuerId, request.petId, request.offerType,
                    request.amount, request.currency, request.inKindDescription, request.message, "SENT", now
                )
            }
        }
        return denormalize(raw)
    }

    override suspend fun getById(id: Int): SponsorshipOfferDto? {
        val raw = withContext(dbDispatcher) {
            transaction {
                SponsorshipOffers.selectAll()
                    .where { SponsorshipOffers.id eq id }
                    .firstOrNull()
                    ?.let(::rowToRaw)
            }
        }
        return raw?.let { denormalize(it) }
    }

    override suspend fun getForRescuer(rescuerId: Int): List<SponsorshipOfferDto> {
        val raws = withContext(dbDispatcher) {
            transaction {
                SponsorshipOffers.selectAll()
                    .where { SponsorshipOffers.rescuerId eq rescuerId }
                    .orderBy(SponsorshipOffers.createdAt, SortOrder.DESC)
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun getForSponsor(sponsorId: Int): List<SponsorshipOfferDto> {
        val raws = withContext(dbDispatcher) {
            transaction {
                SponsorshipOffers.selectAll()
                    .where { SponsorshipOffers.sponsorId eq sponsorId }
                    .orderBy(SponsorshipOffers.createdAt, SortOrder.DESC)
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun markRead(id: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            SponsorshipOffers.update({ SponsorshipOffers.id eq id }) {
                it[SponsorshipOffers.status] = "READ"
            } > 0
        }
    }
}
