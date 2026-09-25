package com.tanidikvar.api.push.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PushDeviceRepository {
    private final JdbcTemplate jdbc;
    public PushDeviceRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void register(UUID user, UUID family, String token, String platform) {
        // Token may move between accounts on one installation. Serialize that move across instances.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", token);
        jdbc.update("UPDATE push_devices SET push_token=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE push_token=? AND family_id<>?",token,family);
        jdbc.update("""
            INSERT INTO push_devices(id,user_id,family_id,push_token,platform) VALUES(?,?,?,?,?)
            ON CONFLICT(family_id) DO UPDATE SET version=push_devices.version+CASE WHEN push_devices.push_token IS DISTINCT FROM excluded.push_token THEN 1 ELSE 0 END,push_token=excluded.push_token,platform=excluded.platform,updated_at=CURRENT_TIMESTAMP
            """,UUID.randomUUID(),user,family,token,platform);
    }
    public void unregister(UUID user, UUID family) {
        jdbc.update("UPDATE push_devices SET push_token=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND family_id=?",user,family);
    }
}
