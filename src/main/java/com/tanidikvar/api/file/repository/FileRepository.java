package com.tanidikvar.api.file.repository;
import com.tanidikvar.api.file.entity.StoredFile;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class FileRepository {
 private final JdbcTemplate jdbc;
 public FileRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void create(UUID id,UUID owner,String purpose,String type,long size){jdbc.update("INSERT INTO stored_files(id,owner_id,purpose,storage_key,original_name,content_type,byte_size,upload_status) VALUES (?,?,?,?,?,?,?,'UPLOADING')",id,owner,purpose,id.toString(),purpose.equals("AVATAR")?"avatar.png":"belge.pdf",type,size);}
 public long recent(UUID owner){return jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE owner_id=? AND created_at>clock_timestamp()-interval '1 hour'",Long.class,owner);}
 public void ready(UUID id){if(jdbc.update("UPDATE stored_files SET upload_status='READY',updated_at=clock_timestamp(),version=version+1 WHERE id=? AND upload_status='UPLOADING' AND deleted_at IS NULL",id)!=1)throw new IllegalStateException("Upload not available");}
 public void fail(UUID id){jdbc.update("UPDATE stored_files SET upload_status='FAILED',deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE id=? AND upload_status='UPLOADING'",id);}
 public void recover(){jdbc.update("UPDATE stored_files SET upload_status='FAILED',deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE upload_status='UPLOADING' AND created_at<clock_timestamp()-interval '1 hour'");}
 public void removeAvatar(UUID owner){jdbc.update("UPDATE stored_files SET deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE owner_id=? AND purpose='AVATAR' AND upload_status='READY' AND deleted_at IS NULL",owner);}
 public Optional<StoredFile> find(UUID id){return jdbc.query("SELECT f.* FROM stored_files f JOIN users u ON u.id=f.owner_id AND u.deleted_at IS NULL WHERE f.id=? AND f.deleted_at IS NULL AND f.upload_status='READY' AND (f.purpose='AVATAR' OR EXISTS (SELECT 1 FROM admin_applications a WHERE a.document_file_id=f.id AND a.deleted_at IS NULL))",(r,n)->new StoredFile(r.getObject("id",UUID.class),r.getObject("owner_id",UUID.class),r.getString("purpose"),r.getString("storage_key"),r.getString("content_type"),r.getLong("byte_size")),id).stream().findFirst();}
 public Optional<UUID> avatar(UUID owner){return jdbc.query("SELECT f.id FROM stored_files f JOIN user_profiles p ON p.user_id=f.owner_id AND p.deleted_at IS NULL JOIN users u ON u.id=f.owner_id AND u.deleted_at IS NULL WHERE f.owner_id=? AND f.purpose='AVATAR' AND f.upload_status='READY' AND f.deleted_at IS NULL",(r,n)->r.getObject("id",UUID.class),owner).stream().findFirst();}
}

