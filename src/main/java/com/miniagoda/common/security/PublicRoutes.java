package com.miniagoda.common.security;

public final class PublicRoutes {

    private PublicRoutes() {}

    public static final String[][] MATCHERS = {
        {"POST", "/api/auth/**"},
        {"GET",  "/api/hotels/**"},
        {"GET",  "/api/search/**"},
    };
}