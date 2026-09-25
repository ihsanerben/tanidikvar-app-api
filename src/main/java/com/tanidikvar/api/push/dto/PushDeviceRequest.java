package com.tanidikvar.api.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushDeviceRequest(
        @NotBlank @Size(max=250) @Pattern(regexp="^(ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]+\\]$") String pushToken,
        @NotBlank @Pattern(regexp="IOS|ANDROID") String platform) {
    @Override public String toString() { return "PushDeviceRequest[REDACTED]"; }
}
