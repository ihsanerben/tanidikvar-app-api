package com.tanidikvar.api.auth.exception;

public class AuthRejectedException extends RuntimeException {
    public AuthRejectedException() { super("Authentication rejected"); }
}
