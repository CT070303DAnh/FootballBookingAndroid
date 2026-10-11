package com.example.footballbooking.ui.auth;

/** Explicit destinations: unknown roles never inherit customer access. */
public enum RoleDestination {
    CUSTOMER, OWNER, ADMIN, PENDING, DENIED;

    public static RoleDestination fromRole(String role) {
        if ("customer".equals(role)) return CUSTOMER;
        if ("owner".equals(role)) return OWNER;
        if ("admin".equals(role)) return ADMIN;
        if ("owner_pending".equals(role)) return PENDING;
        return DENIED;
    }
}
