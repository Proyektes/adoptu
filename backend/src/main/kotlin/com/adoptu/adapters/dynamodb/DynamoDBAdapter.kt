package com.adoptu.adapters.dynamodb

import com.adoptu.dto.input.*
import com.adoptu.ports.DynamoDBPort
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*

class DynamoDBAdapter(
    private val client: DynamoDbAsyncClient,
    private val tableNamePrefix: String = ""
) : DynamoDBPort {

    companion object {
        const val USERS_TABLE = "users"
        const val PETS_TABLE = "pets"
        const val PHOTOGRAPHERS_TABLE = "photographers"
        const val SHELTERS_TABLE = "shelters"
        const val STERILIZATION_LOCATIONS_TABLE = "sterilization_locations"
        const val TEMPORAL_HOMES_TABLE = "temporal_homes"
    }

    private fun tableName(baseName: String): String = "$tableNamePrefix$baseName"

    // User operations
    override suspend fun createUser(user: UserDto): UserDto? {
        val item = mutableMapOf<String, AttributeValue>()
        item["id"] = AttributeValue.builder().n(user.id.toString()).build()
        item["username"] = AttributeValue.builder().s(user.username).build()
        user.email?.let { item["email"] = AttributeValue.builder().s(it).build() }
        item["display_name"] = AttributeValue.builder().s(user.displayName).build()
        item["language"] = AttributeValue.builder().s(user.language).build()
        item["is_email_verified"] = AttributeValue.builder().bool(user.isEmailVerified).build()
        if (user.activeRoles.isNotEmpty()) {
            item["active_roles"] = AttributeValue.builder().ss(user.activeRoles.map { it.name }).build()
        }
        user.lastAcceptedPrivacyPolicy?.let { item["last_accepted_privacy_policy"] = AttributeValue.builder().n(it.toString()).build() }
        user.lastAcceptedTermsAndConditions?.let { item["last_accepted_terms_and_conditions"] = AttributeValue.builder().n(it.toString()).build() }
        item["is_banned"] = AttributeValue.builder().bool(user.isBanned).build()
        user.banReason?.let { item["ban_reason"] = AttributeValue.builder().s(it).build() }

        val request = PutItemRequest.builder()
            .tableName(tableName(USERS_TABLE))
            .item(item)
            .build()

        return try {
            client.putItem(request).await()
            user
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getUserById(userId: Int): UserDto? {
        val key = mapOf("id" to AttributeValue.builder().n(userId.toString()).build())

        val request = GetItemRequest.builder()
            .tableName(tableName(USERS_TABLE))
            .key(key)
            .build()

        return try {
            val response = client.getItem(request).await()
            if (response.hasItem()) {
                response.item().toUserDto()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getUserByEmail(email: String): UserDto? {
        val request = ScanRequest.builder()
            .tableName(tableName(USERS_TABLE))
            .filterExpression("username = :email")
            .expressionAttributeValues(mapOf(":email" to AttributeValue.builder().s(email).build()))
            .build()

        return try {
            val response = client.scan(request).await()
            response.items().firstOrNull()?.toUserDto()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateUser(user: UserDto): UserDto? {
        return createUser(user)
    }

    override suspend fun deleteUser(userId: Int): Boolean {
        val key = mapOf("id" to AttributeValue.builder().n(userId.toString()).build())

        val request = DeleteItemRequest.builder()
            .tableName(tableName(USERS_TABLE))
            .key(key)
            .build()

        return try {
            client.deleteItem(request).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getAllUsers(): List<UserDto> {
        val request = ScanRequest.builder()
            .tableName(tableName(USERS_TABLE))
            .build()

        return try {
            val response = client.scan(request).await()
            response.items().map { it.toUserDto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getUsersByRole(role: UserRole): List<UserDto> {
        return getAllUsers().filter { it.activeRoles.contains(role) }
    }

    // Pet operations
    override suspend fun createPet(pet: PetDto): PetDto? {
        val item = mutableMapOf<String, AttributeValue>()
        item["id"] = AttributeValue.builder().n(pet.id.toString()).build()
        item["rescuer_id"] = AttributeValue.builder().n(pet.rescuerId.toString()).build()
        item["name"] = AttributeValue.builder().s(pet.name).build()
        item["type"] = AttributeValue.builder().s(pet.type).build()
        item["description"] = AttributeValue.builder().s(pet.description).build()
        item["weight"] = AttributeValue.builder().n(pet.weight.toString()).build()
        item["age_years"] = AttributeValue.builder().n(pet.ageYears.toString()).build()
        item["age_months"] = AttributeValue.builder().n(pet.ageMonths.toString()).build()
        item["sex"] = AttributeValue.builder().s(pet.sex.name).build()
        item["status"] = AttributeValue.builder().s(pet.status.name).build()
        pet.breed?.let { item["breed"] = AttributeValue.builder().s(it).build() }
        pet.color?.let { item["color"] = AttributeValue.builder().s(it).build() }
        pet.size?.let { item["size"] = AttributeValue.builder().s(it).build() }
        pet.temperament?.let { item["temperament"] = AttributeValue.builder().s(it).build() }
        item["is_sterilized"] = AttributeValue.builder().bool(pet.isSterilized).build()
        item["is_microchipped"] = AttributeValue.builder().bool(pet.isMicrochipped).build()
        pet.microchipId?.let { item["microchip_id"] = AttributeValue.builder().s(it).build() }
        pet.vaccinations?.let { item["vaccinations"] = AttributeValue.builder().s(it).build() }
        item["is_good_with_kids"] = AttributeValue.builder().bool(pet.isGoodWithKids).build()
        item["is_good_with_dogs"] = AttributeValue.builder().bool(pet.isGoodWithDogs).build()
        item["is_good_with_cats"] = AttributeValue.builder().bool(pet.isGoodWithCats).build()
        item["is_house_trained"] = AttributeValue.builder().bool(pet.isHouseTrained).build()
        pet.energyLevel?.let { item["energy_level"] = AttributeValue.builder().s(it).build() }
        pet.rescueDate?.let { item["rescue_date"] = AttributeValue.builder().n(it.toString()).build() }
        pet.rescueLocation?.let { item["rescue_location"] = AttributeValue.builder().s(it).build() }
        pet.specialNeeds?.let { item["special_needs"] = AttributeValue.builder().s(it).build() }
        item["adoption_fee"] = AttributeValue.builder().n(pet.adoptionFee.toString()).build()
        item["currency"] = AttributeValue.builder().s(pet.currency.name).build()
        item["is_urgent"] = AttributeValue.builder().bool(pet.isUrgent).build()
        item["is_promoted"] = AttributeValue.builder().bool(pet.isPromoted).build()
        item["created_at"] = AttributeValue.builder().n(pet.createdAt.toString()).build()

        val request = PutItemRequest.builder()
            .tableName(tableName(PETS_TABLE))
            .item(item)
            .build()

        return try {
            client.putItem(request).await()
            pet
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPetById(petId: Int): PetDto? {
        val key = mapOf("id" to AttributeValue.builder().n(petId.toString()).build())

        val request = GetItemRequest.builder()
            .tableName(tableName(PETS_TABLE))
            .key(key)
            .build()

        return try {
            val response = client.getItem(request).await()
            if (response.hasItem()) {
                response.item().toPetDto()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updatePet(pet: PetDto): PetDto? = createPet(pet)

    override suspend fun deletePet(petId: Int): Boolean {
        val key = mapOf("id" to AttributeValue.builder().n(petId.toString()).build())

        val request = DeleteItemRequest.builder()
            .tableName(tableName(PETS_TABLE))
            .key(key)
            .build()

        return try {
            client.deleteItem(request).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getAllPets(): List<PetDto> {
        val request = ScanRequest.builder()
            .tableName(tableName(PETS_TABLE))
            .build()

        return try {
            val response = client.scan(request).await()
            response.items().map { it.toPetDto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPetsByRescuer(rescuerId: Int): List<PetDto> {
        val request = ScanRequest.builder()
            .tableName(tableName(PETS_TABLE))
            .filterExpression("rescuer_id = :rescuerId")
            .expressionAttributeValues(mapOf(":rescuerId" to AttributeValue.builder().n(rescuerId.toString()).build()))
            .build()

        return try {
            val response = client.scan(request).await()
            response.items().map { it.toPetDto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Photographer operations
    override suspend fun createPhotographer(photographer: PhotographerDto): PhotographerDto? {
        val item = mutableMapOf<String, AttributeValue>()
        item["user_id"] = AttributeValue.builder().n(photographer.userId.toString()).build()
        item["display_name"] = AttributeValue.builder().s(photographer.displayName).build()
        photographer.username?.let { item["username"] = AttributeValue.builder().s(it).build() }
        photographer.photographerFee?.let { item["photographer_fee"] = AttributeValue.builder().n(it.toString()).build() }
        photographer.photographerCurrency?.let { item["photographer_currency"] = AttributeValue.builder().s(it).build() }
        photographer.country?.let { item["country"] = AttributeValue.builder().s(it).build() }
        photographer.state?.let { item["state"] = AttributeValue.builder().s(it).build() }

        val request = PutItemRequest.builder()
            .tableName(tableName(PHOTOGRAPHERS_TABLE))
            .item(item)
            .build()

        return try {
            client.putItem(request).await()
            photographer
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPhotographerByUserId(userId: Int): PhotographerDto? {
        val key = mapOf("user_id" to AttributeValue.builder().n(userId.toString()).build())
        val request = GetItemRequest.builder()
            .tableName(tableName(PHOTOGRAPHERS_TABLE))
            .key(key)
            .build()

        return try {
            val response = client.getItem(request).await()
            if (response.hasItem()) response.item().toPhotographerDto() else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updatePhotographer(photographer: PhotographerDto): PhotographerDto? = createPhotographer(photographer)

    override suspend fun deletePhotographer(userId: Int): Boolean {
        val key = mapOf("user_id" to AttributeValue.builder().n(userId.toString()).build())
        val request = DeleteItemRequest.builder()
            .tableName(tableName(PHOTOGRAPHERS_TABLE))
            .key(key)
            .build()

        return try {
            client.deleteItem(request).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getAllPhotographers(): List<PhotographerDto> {
        val request = ScanRequest.builder().tableName(tableName(PHOTOGRAPHERS_TABLE)).build()
        return try {
            client.scan(request).await().items().map { it.toPhotographerDto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Shelter operations
    override suspend fun createShelter(shelter: ShelterDto): ShelterDto? {
        val item = mutableMapOf<String, AttributeValue>()
        item["id"] = AttributeValue.builder().n(shelter.id.toString()).build()
        shelter.userId?.let { item["user_id"] = AttributeValue.builder().n(it.toString()).build() }
        item["name"] = AttributeValue.builder().s(shelter.name).build()
        item["country"] = AttributeValue.builder().s(shelter.country).build()
        shelter.state?.let { item["state"] = AttributeValue.builder().s(it).build() }
        item["city"] = AttributeValue.builder().s(shelter.city).build()
        shelter.neighborhood?.let { item["neighborhood"] = AttributeValue.builder().s(it).build() }
        item["address"] = AttributeValue.builder().s(shelter.address).build()
        shelter.zip?.let { item["zip"] = AttributeValue.builder().s(it).build() }
        shelter.phone?.let { item["phone"] = AttributeValue.builder().s(it).build() }
        shelter.email?.let { item["email"] = AttributeValue.builder().s(it).build() }
        shelter.website?.let { item["website"] = AttributeValue.builder().s(it).build() }
        shelter.fiscalId?.let { item["fiscal_id"] = AttributeValue.builder().s(it).build() }
        shelter.bankName?.let { item["bank_name"] = AttributeValue.builder().s(it).build() }
        shelter.accountHolderName?.let { item["account_holder_name"] = AttributeValue.builder().s(it).build() }
        shelter.accountNumber?.let { item["account_number"] = AttributeValue.builder().s(it).build() }
        shelter.iban?.let { item["iban"] = AttributeValue.builder().s(it).build() }
        shelter.swiftBic?.let { item["swift_bic"] = AttributeValue.builder().s(it).build() }
        item["currency"] = AttributeValue.builder().s(shelter.currency).build()
        shelter.description?.let { item["description"] = AttributeValue.builder().s(it).build() }
        item["created_at"] = AttributeValue.builder().n(shelter.createdAt.toString()).build()
        item["updated_at"] = AttributeValue.builder().n(shelter.updatedAt.toString()).build()

        val request = PutItemRequest.builder()
            .tableName(tableName(SHELTERS_TABLE))
            .item(item)
            .build()

        return try {
            client.putItem(request).await()
            shelter
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getShelterByUserId(userId: Int): ShelterDto? {
        val request = ScanRequest.builder()
            .tableName(tableName(SHELTERS_TABLE))
            .filterExpression("user_id = :userId")
            .expressionAttributeValues(mapOf(":userId" to AttributeValue.builder().n(userId.toString()).build()))
            .build()

        return try {
            client.scan(request).await().items().firstOrNull()?.toShelterDto()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateShelter(shelter: ShelterDto): ShelterDto? = createShelter(shelter)

    override suspend fun deleteShelter(userId: Int): Boolean {
        val shelter = getShelterByUserId(userId)
        if (shelter != null) {
            val key = mapOf("id" to AttributeValue.builder().n(shelter.id.toString()).build())
            val request = DeleteItemRequest.builder()
                .tableName(tableName(SHELTERS_TABLE))
                .key(key)
                .build()
            return try {
                client.deleteItem(request).await()
                true
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    override suspend fun getAllShelters(): List<ShelterDto> {
        val request = ScanRequest.builder().tableName(tableName(SHELTERS_TABLE)).build()
        return try {
            client.scan(request).await().items().map { it.toShelterDto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Sterilization Location operations
    override suspend fun createSterilizationLocation(location: SterilizationLocationDto): SterilizationLocationDto? {
        val item = mutableMapOf<String, AttributeValue>()
        item["id"] = AttributeValue.builder().n(location.id.toString()).build()
        location.userId?.let { item["user_id"] = AttributeValue.builder().n(it.toString()).build() }
        item["name"] = AttributeValue.builder().s(location.name).build()
        item["country"] = AttributeValue.builder().s(location.country).build()
        location.state?.let { item["state"] = AttributeValue.builder().s(it).build() }
        item["city"] = AttributeValue.builder().s(location.city).build()
        location.neighborhood?.let { item["neighborhood"] = AttributeValue.builder().s(it).build() }
        item["address"] = AttributeValue.builder().s(location.address).build()
        location.zip?.let { item["zip"] = AttributeValue.builder().s(it).build() }
        location.phone?.let { item["phone"] = AttributeValue.builder().s(it).build() }
        location.email?.let { item["email"] = AttributeValue.builder().s(it).build() }
        location.website?.let { item["website"] = AttributeValue.builder().s(it).build() }
        location.description?.let { item["description"] = AttributeValue.builder().s(it).build() }
        item["created_at"] = AttributeValue.builder().n(location.createdAt.toString()).build()
        item["updated_at"] = AttributeValue.builder().n(location.updatedAt.toString()).build()

        val request = PutItemRequest.builder()
            .tableName(tableName(STERILIZATION_LOCATIONS_TABLE))
            .item(item)
            .build()

        return try {
            client.putItem(request).await()
            location
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getSterilizationLocationByUserId(userId: Int): SterilizationLocationDto? {
        val request = ScanRequest.builder()
            .tableName(tableName(STERILIZATION_LOCATIONS_TABLE))
            .filterExpression("user_id = :userId")
            .expressionAttributeValues(mapOf(":userId" to AttributeValue.builder().n(userId.toString()).build()))
            .build()

        return try {
            client.scan(request).await().items().firstOrNull()?.toSterilizationLocationDto()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateSterilizationLocation(location: SterilizationLocationDto): SterilizationLocationDto? = createSterilizationLocation(location)

    override suspend fun deleteSterilizationLocation(userId: Int): Boolean {
        val location = getSterilizationLocationByUserId(userId)
        if (location != null) {
            val key = mapOf("id" to AttributeValue.builder().n(location.id.toString()).build())
            val request = DeleteItemRequest.builder()
                .tableName(tableName(STERILIZATION_LOCATIONS_TABLE))
                .key(key)
                .build()
            return try {
                client.deleteItem(request).await()
                true
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    override suspend fun getAllSterilizationLocations(): List<SterilizationLocationDto> {
        val request = ScanRequest.builder().tableName(tableName(STERILIZATION_LOCATIONS_TABLE)).build()
        return try {
            client.scan(request).await().items().map { it.toSterilizationLocationDto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Temporal Home operations
    override suspend fun createTemporalHome(temporalHome: TemporalHomeDto): TemporalHomeDto? {
        val item = mutableMapOf<String, AttributeValue>()
        item["user_id"] = AttributeValue.builder().n(temporalHome.userId.toString()).build()
        item["alias"] = AttributeValue.builder().s(temporalHome.alias).build()
        item["country"] = AttributeValue.builder().s(temporalHome.country).build()
        temporalHome.state?.let { item["state"] = AttributeValue.builder().s(it).build() }
        item["city"] = AttributeValue.builder().s(temporalHome.city).build()
        temporalHome.neighborhood?.let { item["neighborhood"] = AttributeValue.builder().s(it).build() }
        temporalHome.zip?.let { item["zip"] = AttributeValue.builder().s(it).build() }
        item["created_at"] = AttributeValue.builder().n(temporalHome.createdAt.toString()).build()

        val request = PutItemRequest.builder()
            .tableName(tableName(TEMPORAL_HOMES_TABLE))
            .item(item)
            .build()

        return try {
            client.putItem(request).await()
            temporalHome
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getTemporalHomeByUserId(userId: Int): TemporalHomeDto? {
        val key = mapOf("user_id" to AttributeValue.builder().n(userId.toString()).build())
        val request = GetItemRequest.builder()
            .tableName(tableName(TEMPORAL_HOMES_TABLE))
            .key(key)
            .build()

        return try {
            val response = client.getItem(request).await()
            if (response.hasItem()) response.item().toTemporalHomeDto() else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateTemporalHome(temporalHome: TemporalHomeDto): TemporalHomeDto? = createTemporalHome(temporalHome)

    override suspend fun deleteTemporalHome(userId: Int): Boolean {
        val key = mapOf("user_id" to AttributeValue.builder().n(userId.toString()).build())
        val request = DeleteItemRequest.builder()
            .tableName(tableName(TEMPORAL_HOMES_TABLE))
            .key(key)
            .build()

        return try {
            client.deleteItem(request).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getAllTemporalHomes(): List<TemporalHomeDto> {
        val request = ScanRequest.builder().tableName(tableName(TEMPORAL_HOMES_TABLE)).build()
        return try {
            client.scan(request).await().items().map { it.toTemporalHomeDto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Helper functions to convert between DynamoDB items and DTOs
    private fun Map<String, AttributeValue>.toUserDto(): UserDto {
        val id = this["id"]?.n()?.toInt() ?: 0
        val username = this["username"]?.s() ?: ""
        val email = this["email"]?.s()
        val displayName = this["display_name"]?.s() ?: ""
        val language = this["language"]?.s() ?: "en"
        val isEmailVerified = this["is_email_verified"]?.bool() ?: false
        val activeRoles = this["active_roles"]?.ss()?.mapNotNull { roleStr ->
            try { UserRole.valueOf(roleStr) } catch (e: Exception) { null }
        }?.toSet() ?: emptySet()
        val lastAcceptedPrivacyPolicy = this["last_accepted_privacy_policy"]?.n()?.toLong()
        val lastAcceptedTermsAndConditions = this["last_accepted_terms_and_conditions"]?.n()?.toLong()
        val isBanned = this["is_banned"]?.bool() ?: false
        val banReason = this["ban_reason"]?.s()

        return UserDto(
            id = id,
            username = username,
            email = email,
            displayName = displayName,
            language = language,
            isEmailVerified = isEmailVerified,
            activeRoles = activeRoles,
            lastAcceptedPrivacyPolicy = lastAcceptedPrivacyPolicy,
            lastAcceptedTermsAndConditions = lastAcceptedTermsAndConditions,
            isBanned = isBanned,
            banReason = banReason
        )
    }

    private fun Map<String, AttributeValue>.toPetDto(): PetDto {
        val id = this["id"]?.n()?.toInt() ?: 0
        val rescuerId = this["rescuer_id"]?.n()?.toInt() ?: 0
        val name = this["name"]?.s() ?: ""
        val type = this["type"]?.s() ?: "DOG"
        val breed = this["breed"]?.s()
        val description = this["description"]?.s() ?: ""
        val weight = this["weight"]?.n()?.toDouble() ?: 0.0
        val ageYears = this["age_years"]?.n()?.toInt() ?: 0
        val ageMonths = this["age_months"]?.n()?.toInt() ?: 0
        val sex = this["sex"]?.s() ?: "MALE"
        val status = this["status"]?.s() ?: "AVAILABLE"
        val color = this["color"]?.s()
        val size = this["size"]?.s()
        val temperament = this["temperament"]?.s()
        val isSterilized = this["is_sterilized"]?.bool() ?: false
        val isMicrochipped = this["is_microchipped"]?.bool() ?: false
        val microchipId = this["microchip_id"]?.s()
        val vaccinations = this["vaccinations"]?.s()
        val isGoodWithKids = this["is_good_with_kids"]?.bool() ?: true
        val isGoodWithDogs = this["is_good_with_dogs"]?.bool() ?: true
        val isGoodWithCats = this["is_good_with_cats"]?.bool() ?: true
        val isHouseTrained = this["is_house_trained"]?.bool() ?: false
        val energyLevel = this["energy_level"]?.s()
        val rescueDate = this["rescue_date"]?.n()?.toLong()
        val rescueLocation = this["rescue_location"]?.s()
        val specialNeeds = this["special_needs"]?.s()
        val adoptionFee = this["adoption_fee"]?.n()?.toDouble() ?: 0.0
        val currency = this["currency"]?.s() ?: "USD"
        val isUrgent = this["is_urgent"]?.bool() ?: false
        val isPromoted = this["is_promoted"]?.bool() ?: false

        return PetDto(
            id = id,
            rescuerId = rescuerId,
            name = name,
            type = type,
            breed = breed,
            description = description,
            weight = weight,
            ageYears = ageYears,
            ageMonths = ageMonths,
            sex = if (sex == "FEMALE") Gender.FEMALE else Gender.MALE,
            status = try { Status.valueOf(status) } catch (e: Exception) { Status.AVAILABLE },
            color = color,
            size = size,
            temperament = temperament,
            isSterilized = isSterilized,
            isMicrochipped = isMicrochipped,
            microchipId = microchipId,
            vaccinations = vaccinations,
            isGoodWithKids = isGoodWithKids,
            isGoodWithDogs = isGoodWithDogs,
            isGoodWithCats = isGoodWithCats,
            isHouseTrained = isHouseTrained,
            energyLevel = energyLevel,
            rescueDate = rescueDate,
            rescueLocation = rescueLocation,
            specialNeeds = specialNeeds,
            adoptionFee = adoptionFee,
            currency = try { Currency.valueOf(currency) } catch (e: Exception) { Currency.USD },
            isUrgent = isUrgent,
            isPromoted = isPromoted,
            createdAt = this["created_at"]?.n()?.toLong() ?: 0
        )
    }

    private fun Map<String, AttributeValue>.toPhotographerDto(): PhotographerDto {
        return PhotographerDto(
            userId = this["user_id"]?.n()?.toInt() ?: 0,
            displayName = this["display_name"]?.s() ?: "",
            username = this["username"]?.s(),
            photographerFee = this["photographer_fee"]?.n()?.toDouble(),
            photographerCurrency = this["photographer_currency"]?.s(),
            country = this["country"]?.s(),
            state = this["state"]?.s()
        )
    }

    private fun Map<String, AttributeValue>.toShelterDto(): ShelterDto {
        return ShelterDto(
            id = this["id"]?.n()?.toInt() ?: 0,
            userId = this["user_id"]?.n()?.toInt(),
            name = this["name"]?.s() ?: "",
            country = this["country"]?.s() ?: "",
            state = this["state"]?.s(),
            city = this["city"]?.s() ?: "",
            neighborhood = this["neighborhood"]?.s(),
            address = this["address"]?.s() ?: "",
            zip = this["zip"]?.s(),
            phone = this["phone"]?.s(),
            email = this["email"]?.s(),
            website = this["website"]?.s(),
            fiscalId = this["fiscal_id"]?.s(),
            bankName = this["bank_name"]?.s(),
            accountHolderName = this["account_holder_name"]?.s(),
            accountNumber = this["account_number"]?.s(),
            iban = this["iban"]?.s(),
            swiftBic = this["swift_bic"]?.s(),
            currency = this["currency"]?.s() ?: "USD",
            description = this["description"]?.s(),
            createdAt = this["created_at"]?.n()?.toLong() ?: 0,
            updatedAt = this["updated_at"]?.n()?.toLong() ?: 0
        )
    }

    private fun Map<String, AttributeValue>.toSterilizationLocationDto(): SterilizationLocationDto {
        return SterilizationLocationDto(
            id = this["id"]?.n()?.toInt() ?: 0,
            userId = this["user_id"]?.n()?.toInt(),
            name = this["name"]?.s() ?: "",
            country = this["country"]?.s() ?: "",
            state = this["state"]?.s(),
            city = this["city"]?.s() ?: "",
            neighborhood = this["neighborhood"]?.s(),
            address = this["address"]?.s() ?: "",
            zip = this["zip"]?.s(),
            phone = this["phone"]?.s(),
            email = this["email"]?.s(),
            website = this["website"]?.s(),
            description = this["description"]?.s(),
            createdAt = this["created_at"]?.n()?.toLong() ?: 0,
            updatedAt = this["updated_at"]?.n()?.toLong() ?: 0
        )
    }

    private fun Map<String, AttributeValue>.toTemporalHomeDto(): TemporalHomeDto {
        return TemporalHomeDto(
            userId = this["user_id"]?.n()?.toInt() ?: 0,
            alias = this["alias"]?.s() ?: "",
            country = this["country"]?.s() ?: "",
            state = this["state"]?.s(),
            city = this["city"]?.s() ?: "",
            zip = this["zip"]?.s(),
            neighborhood = this["neighborhood"]?.s(),
            createdAt = this["created_at"]?.n()?.toLong() ?: 0
        )
    }
}
