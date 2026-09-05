package com.tanidikvar.api.auth.exception;

public class PasswordFormatException extends RuntimeException {
    public PasswordFormatException() { super("Password exceeds encoded length limit"); }
}
