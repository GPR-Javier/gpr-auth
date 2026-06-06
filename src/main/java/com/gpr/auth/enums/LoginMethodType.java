package com.gpr.auth.enums;

/** A way a user can authenticate. PASSWORD is the only one wired today; the rest are future. */
public enum LoginMethodType {
    PASSWORD,
    GOOGLE,
    MICROSOFT,
    MAGIC_LINK
}
