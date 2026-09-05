package com.tanidikvar.api.auth.exception;
public class EmailUnverifiedException extends RuntimeException {
    public EmailUnverifiedException() { super("Email verification required"); }
}
