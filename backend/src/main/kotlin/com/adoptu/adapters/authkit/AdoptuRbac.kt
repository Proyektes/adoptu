package com.adoptu.adapters.authkit

import com.adoptu.dto.input.UserRole
import com.universaliun.auth.common.rbac.Crud
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
 * (see [Crud.entries]) on the one resource its own profile type owns - ADOPTER/URGENT_RESCUER own
 * none, since their authorization is entirely role-membership (or row-level ownership, decided in
 * the service layer) at every current call site, never resource-CRUD, so no partial-CRUD subset is
 * meaningful here today. [ADMIN.grantsAll] is a host-side signal, not itself checked at
 * authorization time (AuthKit 1.3.0+): [AdoptuUserRepositoryAdapter.toAuthUser] reads it to grant
 * [com.universaliun.auth.common.rbac.AuthPrincipal.SUPER_ADMIN_ACTION] as an allowed action, and
 * every hasResource/hasPermission/hasRole/isSuperAdmin check bypasses via that allowed action, not
 * via role identity - see [com.universaliun.auth.common.rbac.AuthPrincipal.isSuperAdmin]'s doc
 * comment.
 */
enum class AdoptuRole(override val resources: Map<Resource, Set<Crud>>) : Role {
    ADOPTER(emptyMap()),
    RESCUER(mapOf(AdoptuResource.PET to Crud.entries.toSet())),
    PHOTOGRAPHER(mapOf(AdoptuResource.PHOTOGRAPHER to Crud.entries.toSet())),
    TEMPORAL_HOME(mapOf(AdoptuResource.TEMPORAL_HOME to Crud.entries.toSet())),
    SHELTER(mapOf(AdoptuResource.SHELTER to Crud.entries.toSet())),
    STERILIZATION_SERVICE(mapOf(AdoptuResource.STERILIZATION_LOCATION to Crud.entries.toSet())),
    URGENT_RESCUER(emptyMap()),
    ADMIN(emptyMap()) {
        override fun grantsAll() = true
    },
    ;

    override fun grantsAll(): Boolean = false
}

/** [com.universaliun.auth.backend.infrastructure.authKoinModule]'s / `installJwtAuth`'s
 *  `roleByName` - resolves a JWT role-name claim back to our own [AdoptuRole] constant. */
val adoptuRoleByName: (String) -> Role? = { name -> runCatching { AdoptuRole.valueOf(name) }.getOrNull() }
