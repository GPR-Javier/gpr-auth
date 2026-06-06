# JWT Claim Contract (GPR Identity)

The canonical contract for tokens issued by **gpr-auth** (the renamed/gutted wos-auth) and validated
by every consuming app (WorkOS / wos-hr, and future petvet, rental).

There is **no shared library** for this — each app validates tokens independently using the shared
signing key + the claim names below. This document **is** the contract. Keep apps in sync by hand.

> Status: this describes the **target** contract. Items marked `(current)` already exist in
> `JwtService`; items marked `(Phase 4)` are added when multi-app login is wired up.

---

## Core identity claims — issued by gpr-auth, app-agnostic

| Claim   | Type            | Meaning                                              | Status     |
|---------|-----------------|------------------------------------------------------|------------|
| `sub`   | string (Long)   | Global user id. **`Long`, not UUID** (centralized, single-writer — see plan). Parsed with `Long.parseLong`. | (current)  |
| `email` | string          | User email (unique identity key).                    | (current)  |
| `name`  | string          | Display name (`firstName + " " + lastName`).         | (Phase 4)  |
| `aud`   | string          | The app this token is for: `workos` \| `petvet` \| `rental`. | (Phase 4)  |
| `iat`   | number (epoch)  | Issued-at.                                           | (current)  |
| `exp`   | number (epoch)  | Expiry. Access: 1h. Refresh: 7d.                    | (current)  |

Refresh tokens carry **only** `sub`, `iat`, `exp` — no roles, no `aud`.

## App-scoped claims — added at login-time enrichment, NOT core identity

These are stamped per-app during token assembly (the app resolves its own roles). They are **not**
part of the identity model and other apps ignore them. WorkOS currently uses:

| Claim           | Type           | Meaning                                          | Status    |
|-----------------|----------------|--------------------------------------------------|-----------|
| `role`          | string         | `ADMIN` \| `EMPLOYEE` (coarse gate).             | (current) |
| `userRoleId`    | number (Long)  | Selected WorkOS user-role id; drives `PermissionLoader`. **Stays `Long`** (WorkOS-local). | (current) |
| `userRoleNames` | string[]       | Names of the user's active WorkOS roles (display). | (current) |

Other products define their own app-scoped claims independently. Do not add a product's claims to
the core set.

---

## Signing & validation rules

- **Algorithm:** HS256, shared secret (`jwt.secret`). Same secret across services today.
- **`aud` rejection (mandatory, Phase 4):** every app MUST reject a token whose `aud` is not its own.
  Because the signing secret is shared, without this check a WorkOS token would unlock petvet.
- **Stronger variant (future):** per-app signing keys (asymmetric + JWKS) so a token is
  cryptographically only verifiable by its intended app — makes the `aud` check defense-in-depth.
- **Enumeration note:** `sub` is a sequential `Long`. Protection against `/users/{id}` walking is
  **authorization** (own-record / admin checks), not id obscurity. URL id-encryption is a deferred,
  targeted-only mitigation — do not build preemptively.

## How a consumer validates (reference: wos-hr)

1. Extract token from `access_token` cookie or `Authorization: Bearer`.
2. Verify signature + expiry with the shared key.
3. (Phase 4) Reject if `aud` != this app's id.
4. Read claims by name (`email`, `role`, `userRoleId`, …) — string literals, no shared DTO.
5. Build authorities (WorkOS: `PermissionLoader.loadByUserRoleId(userRoleId)` + `ROLE_ADMIN` if `role==ADMIN`).

See `wos-hr/.../security/JwtAuthenticationFilter.java` for the working implementation.
