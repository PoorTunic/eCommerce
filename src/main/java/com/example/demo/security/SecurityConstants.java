package com.example.demo.security;

public final class SecurityConstants {

	private SecurityConstants() {
	}

	public static final String SECRET = "ChangeMeInProd_UseEnvVar";
	public static final long EXPIRATION_TIME_MS = 86_400_000; // 1 day
	public static final String TOKEN_PREFIX = "Bearer ";
	public static final String HEADER_STRING = "Authorization";

}
