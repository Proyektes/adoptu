package com.adoptu.adapters.authkit

import com.adoptu.dto.input.UserRole
import com.universaliun.auth.common.rbac.Resource
import com.universaliun.auth.common.rbac.Role

/**
 * Adopt-u's RBAC catalog for AuthKit -- the "what" ([AdoptuResource]) and "who" ([AdoptuRole])
 * halves of the PermissionSet/JWT-claims model AuthKit's JwtAuthFilter/AuthPrincipal decode into
 * on every request (see AuthKoinModule.kt's resourceCount/roleByName docs). [bitBlock] values are
 * permanent once anything encodes a PermissionSet against them (a still-live token minted before a
 * reorder would decode against the wrong resource) - only ever append, never reorder or reuse a
 * freed slot.
 */
enum class AdoptuResource(override val bitBlock: Int) : Resource {
    PET(0),
    SHELTER(1),
    STERILIZATION_LOCATION(2),
    PHOTOGRAPHER(3),
    TEMPORAL_HOME(4),
}

val ADOPTU_RESOURCE_COUNT = AdoptuResource.entries.size

/**
 * Mirrors [UserRole] 1:1 by entry name - [AdoptuRole.valueOf] round-trips cleanly against both a
 * decoded JWT role-name claim and the user_active_roles.role DB column (see
 * [AdoptuUserRepositoryAdapter]), which already stores `UserRole.name`. Each role grants full CRUD
 * (see [com.universaliun.auth.common.rbac.PermissionSet.fromResources]) on the one resource its
 * own profile type owns - ADOPTER/URGENT_RESCUER own none, since their authorization is entirely
 * role-membership (or row-level ownership, decided in the service layer) at every current call
 * site, never resource-CRUD. [ADMIN.grantsAll] bypasses every hasResource/hasPermission/hasRole
 * check via [com.universaliun.auth.common.rbac.AuthPrincipal.isSuperAdmin] - see its doc comment.
 */
enum class AdoptuRole(override val resources: Set<Resource>) : Role {
    ADOPTER(emptySet()),
    RESCUER(setOf(AdoptuResource.PET)),
    PHOTOGRAPHER(setOf(AdoptuResource.PHOTOGRAPHER)),
    TEMPORAL_HOME(setOf(AdoptuResource.TEMPORAL_HOME)),
    SHELTER(setOf(AdoptuResource.SHELTER)),
    STERILIZATION_SERVICE(setOf(AdoptuResource.STERILIZATION_LOCATION)),
    URGENT_RESCUER(emptySet()),
    ADMIN(emptySet()) {
        override fun grantsAll() = true
    },
    ;

    override fun grantsAll(): Boolean = false
}

/** [com.universaliun.auth.backend.infrastructure.authKoinModule]'s / `installJwtAuth`'s
 *  `roleByName` - resolves a JWT role-name claim back to our own [AdoptuRole] constant. */
val adoptuRoleByName: (String) -> Role? = { name -> runCatching { AdoptuRole.valueOf(name) }.getOrNull() }

fun UserRole.toAdoptuRole(): AdoptuRole = AdoptuRole.valueOf(name)
