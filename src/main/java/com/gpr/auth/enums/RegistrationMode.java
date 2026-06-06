package com.gpr.auth.enums;

/** How a company/app allows new users in. Recorded per app in the {@code apps} registry. */
public enum RegistrationMode {
    /** Anyone may create an account (public self-service, e.g. a consumer app). */
    SELF_SIGNUP,
    /** Users are provisioned by an admin/invite only (e.g. WorkOS internal HR). */
    INVITE_ONLY
}
