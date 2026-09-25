package com.tanidikvar.api.push.service;

public interface PushGateway {
    Result send(String token, String targetUrl);
    Result receipt(String ticket);
    record Result(String state, String ticket, String error) { }
}
