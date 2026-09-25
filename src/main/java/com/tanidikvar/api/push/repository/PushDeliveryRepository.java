package com.tanidikvar.api.push.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PushDeliveryRepository {
    private final JdbcTemplate jdbc;
    public PushDeliveryRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Delivery(UUID id, UUID device, String state, int attempt, String ticket) { }
    public List<Delivery> claim() {
        // Atomic leases work across instances; each request is bounded to 10s, batches to 10 rows.
        return jdbc.query("""
            UPDATE push_deliveries SET status=CASE WHEN ticket_id IS NULL THEN 'PROCESSING' ELSE 'RECEIPT' END,
              attempts=attempts+1,locked_at=CURRENT_TIMESTAMP
            WHERE id IN (SELECT id FROM push_deliveries WHERE attempts<6 AND created_at>CURRENT_TIMESTAMP-interval '24 hours'
              AND ((status IN ('PENDING','TICKET') AND due_at<=CURRENT_TIMESTAMP)
                OR (status IN ('PROCESSING','RECEIPT') AND locked_at<CURRENT_TIMESTAMP-interval '5 minutes'))
              ORDER BY due_at,id FOR UPDATE SKIP LOCKED LIMIT 10)
            RETURNING id,device_id,status,attempts,ticket_id
            """,(rs,n)->new Delivery(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getInt(4),rs.getString(5)));
    }
    public record Target(String token, String url) { }
    public Optional<Target> eligibleToken(Delivery delivery) {
        return jdbc.query("""
            SELECT d.push_token,n.target_type,n.target_id FROM push_devices d JOIN push_deliveries pd ON pd.device_id=d.id
            JOIN notifications n ON n.id=pd.notification_id JOIN users u ON u.id=d.user_id
            LEFT JOIN notification_preferences np ON np.user_id=d.user_id
            WHERE pd.id=? AND d.version=pd.device_version AND d.push_token IS NOT NULL AND u.deleted_at IS NULL AND n.deleted_at IS NULL
              AND n.read_at IS NULL AND coalesce(np.in_app_enabled,true)
              AND EXISTS(SELECT 1 FROM auth_sessions s WHERE s.user_id=d.user_id AND s.family_id=d.family_id
                AND s.replaced_by_id IS NULL AND s.revoked_at IS NULL AND s.deleted_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP)
            """,(rs,n)->new Target(rs.getString(1),targetUrl(rs.getString(2),rs.getObject(3,UUID.class))),delivery.id()).stream().findFirst();
    }
    private String targetUrl(String type, UUID id) {
        if(type==null || id==null) return "/bildirimler";
        return switch(type) {
            case "QUESTION" -> "/questions/"+id;
            case "ANSWER" -> "/answers/"+id+"/comments";
            case "UNIVERSITY" -> "/universities/"+id;
            case "PROGRAM" -> "/programs/"+id;
            case "TANIDIK" -> "/profiles/"+id;
            default -> "/bildirimler";
        };
    }
    public void finish(Delivery delivery,String state,String ticket,String error,String token) {
        jdbc.update("""
            UPDATE push_deliveries SET status=?,ticket_id=coalesce(?,ticket_id),last_error=?,
              attempts=CASE WHEN ?='TICKET' AND ticket_id IS NULL THEN 0 ELSE attempts END,
              due_at=CURRENT_TIMESTAMP + (? * interval '1 minute')
            WHERE id=? AND attempts=? AND status=?
            """,state,ticket,error,state,state.equals("TICKET")?15:Math.min(60,1<<delivery.attempt()),delivery.id(),delivery.attempt(),delivery.state());
        if("DEVICE_NOT_REGISTERED".equals(error))
            jdbc.update("UPDATE push_devices SET push_token=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND push_token=?",delivery.device(),token);
    }
    public void expire() {
        jdbc.update("UPDATE push_deliveries SET status='FAILED',last_error='RETRY_EXHAUSTED' WHERE status IN ('PENDING','TICKET','PROCESSING','RECEIPT') AND (created_at<CURRENT_TIMESTAMP-interval '24 hours' OR (attempts>=6 AND (locked_at IS NULL OR locked_at<CURRENT_TIMESTAMP-interval '5 minutes')))");
        jdbc.update("""
            UPDATE push_devices d SET push_token=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE push_token IS NOT NULL
            AND NOT EXISTS(SELECT 1 FROM auth_sessions s WHERE s.user_id=d.user_id AND s.family_id=d.family_id
              AND s.replaced_by_id IS NULL AND s.revoked_at IS NULL AND s.deleted_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP)
            """);
    }
}
