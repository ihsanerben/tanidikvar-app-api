package com.tanidikvar.api.auth.exception;
public class ActionTokenException extends RuntimeException {
    public ActionTokenException() { super("Invalid or expired action token"); }
}
