package com.tanidikvar.api.auth.mapper;

import com.tanidikvar.api.auth.dto.CurrentUserResponse;
import com.tanidikvar.api.auth.entity.Account;
import com.tanidikvar.api.auth.entity.Authority;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    private final com.tanidikvar.api.profile.service.ProfileSummaryService profiles;
    public AccountMapper(com.tanidikvar.api.profile.service.ProfileSummaryService profiles) { this.profiles=profiles; }
    public CurrentUserResponse toResponse(Account account) {
        var education = profiles.status(account.getId());
        String role = account.getAuthority() == Authority.MEMBER ? (education==null ? "USER" : education.name()) : account.getAuthority().name();
        return new CurrentUserResponse(account.getId(), account.getEmail(), role, education!=null);
    }
}
