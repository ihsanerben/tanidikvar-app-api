package com.tanidikvar.api.auth.mapper;

import com.tanidikvar.api.auth.dto.CurrentUserResponse;
import com.tanidikvar.api.auth.entity.Account;
import com.tanidikvar.api.auth.entity.Authority;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    public CurrentUserResponse toResponse(Account account) {
        // Educational profiles are introduced in the next delivery.
        String role = account.getAuthority() == Authority.MEMBER ? "USER" : account.getAuthority().name();
        return new CurrentUserResponse(account.getId(), account.getEmail(), role, false);
    }
}
