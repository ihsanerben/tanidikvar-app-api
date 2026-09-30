package com.tanidikvar.api.decision.service;

import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ContextContributorPolicy {
    private final JdbcTemplate jdbc;
    public ContextContributorPolicy(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void requireMatchingTanidik(UUID userId,UUID universityId,UUID programId) {
        boolean allowed=Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(
              SELECT 1 FROM users account
              JOIN user_profiles profile ON profile.user_id=account.id AND profile.deleted_at IS NULL
              WHERE account.id=? AND account.deleted_at IS NULL AND account.authority='TANIDIK'
                AND profile.university_id=?
                AND (?::uuid IS NULL OR profile.program_id=?::uuid OR EXISTS(
                  SELECT 1 FROM university_departments legacy
                  WHERE legacy.id=?::uuid AND legacy.deleted_at IS NULL
                    AND legacy.university_id=profile.university_id
                    AND legacy.department_id=profile.department_id
                ))
            )
            """,Boolean.class,userId,universityId,programId,programId,programId));
        if(!allowed)throw new DomainException(403,"CONTEXT_TANIDIK_REQUIRED","Bu alana yalnız profilinde ilgili üniversite veya program bulunan Tanıdık katkı yapabilir.");
    }
    public void requireMember(UUID userId,UUID universityId,UUID programId,boolean studentOnly) {
        boolean allowed=Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM users a JOIN user_profiles p ON p.user_id=a.id AND p.deleted_at IS NULL
              WHERE a.id=? AND a.deleted_at IS NULL AND a.authority<>'MANAGER' AND p.university_id=?
              AND (p.education_status='UNIVERSITE_OGRENCISI' OR (?=false AND p.education_status='MEZUN'))
              AND (?::uuid IS NULL OR p.program_id=?::uuid OR EXISTS(SELECT 1 FROM university_departments d
                WHERE d.id=?::uuid AND d.deleted_at IS NULL AND d.university_id=p.university_id AND d.department_id=p.department_id)))
            """,Boolean.class,userId,universityId,studentOnly,programId,programId,programId));
        if(!allowed)throw new DomainException(403,"CONTEXT_MEMBERSHIP_REQUIRED",studentOnly?"Bu ankete yalnız bu üniversitenin öğrencileri katılabilir.":"Bu alana yalnız bu üniversitenin öğrencileri ve mezunları katılabilir.");
    }
}
