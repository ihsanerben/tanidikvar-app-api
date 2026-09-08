package com.tanidikvar.api;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class TestAvatar {
    private TestAvatar() {}

    static void ready(JdbcTemplate jdbc, UUID ownerId) {
        UUID fileId=UUID.randomUUID();
        jdbc.update("INSERT INTO stored_files(id,owner_id,purpose,storage_key,original_name,content_type,byte_size,upload_status) VALUES (?,?,'AVATAR',?,'avatar.png','image/png',100,'READY')",fileId,ownerId,fileId.toString());
    }
}
