package com.tanidikvar.api;

import com.tanidikvar.api.push.service.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class MobilePushIT extends AuthenticationIT {
    @MockitoBean PushGateway gateway;
    @Autowired PushDeliveryProcessor processor;
    private tools.jackson.databind.JsonNode session() throws Exception {
        String email=email();register(email);verify(email);
        return mapper.readTree(mvc.perform(postJson("/api/auth/mobile/login",Map.of("email",email,"password","Test-password-123!")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder device(tools.jackson.databind.JsonNode session,String token) {
        return put("/api/me/push-device").header("Authorization","Bearer "+session.path("accessToken").asText())
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("pushToken",token,"platform","IOS")));
    }
    private UUID note(tools.jackson.databind.JsonNode session) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO notifications(id,user_id,notification_type,title,body) VALUES(?,?,'TEST','Test','Private text')",id,UUID.fromString(session.path("user").path("id").asText()));
        return id;
    }
    @Test void validationOwnershipRotationAndRevocation() throws Exception {
        var a=session();var b=session();String token="ExpoPushToken["+UUID.randomUUID()+"]";
        mvc.perform(put("/api/me/push-device").header("Authorization","Bearer invalid").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(device(a,"invalid")).andExpect(status().isBadRequest());
        mvc.perform(device(a,token)).andExpect(status().isNoContent());
        mvc.perform(device(a,token)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE push_token=?",Long.class,token)).isEqualTo(1);
        mvc.perform(delete("/api/me/push-device").header("Authorization","Bearer "+b.path("accessToken").asText())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE push_token=?",Long.class,token)).isEqualTo(1);
        mvc.perform(postJson("/api/auth/mobile/refresh",Map.of("refreshToken",a.path("refreshToken").asText()))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE push_token=?",Long.class,token)).isEqualTo(1);
        mvc.perform(postJson("/api/auth/mobile/logout",Map.of("refreshToken",a.path("refreshToken").asText()))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE push_token=?",Long.class,token)).isZero();
        mvc.perform(device(a,token)).andExpect(status().isUnauthorized());
        var web=account();
        mvc.perform(put("/api/me/push-device").cookie(access(web)).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
    }
    @Test void concurrentRegistrationIsIdempotentAndCrossAccountMoveStopsOldDelivery() throws Exception {
        var a=session();var b=session();String token="ExpoPushToken["+UUID.randomUUID()+"]";
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> call=()->{start.await();return mvc.perform(device(a,token)).andReturn().getResponse().getStatus();};
            var one=executor.submit(call);var two=executor.submit(call);start.countDown();
            assertThat(one.get(20,TimeUnit.SECONDS)).isEqualTo(204);assertThat(two.get(20,TimeUnit.SECONDS)).isEqualTo(204);
        }
        UUID old=note(a);
        mvc.perform(device(b,token)).andExpect(status().isNoContent());
        processor.process();
        assertThat(jdbc.queryForObject("SELECT status FROM push_deliveries WHERE notification_id=?",String.class,old)).isEqualTo("CANCELLED");
        org.mockito.Mockito.verify(gateway,never()).send(token,"/bildirimler");
    }
    @Test void outboxTicketsReceiptsAndDeadDevices() throws Exception {
        var a=session();String token="ExpoPushToken["+UUID.randomUUID()+"]";
        mvc.perform(device(a,token)).andExpect(status().isNoContent());
        UUID id=note(a);
        when(gateway.send(token,"/bildirimler")).thenReturn(new PushGateway.Result("TICKET","ticket-1",null));
        processor.process();processor.process();
        org.mockito.Mockito.verify(gateway,times(1)).send(token,"/bildirimler");
        assertThat(jdbc.queryForObject("SELECT status FROM push_deliveries WHERE notification_id=?",String.class,id)).isEqualTo("TICKET");
        jdbc.update("UPDATE push_deliveries SET due_at=CURRENT_TIMESTAMP WHERE notification_id=?",id);
        when(gateway.receipt("ticket-1")).thenReturn(new PushGateway.Result("FAILED",null,"DEVICE_NOT_REGISTERED"));
        processor.process();
        assertThat(jdbc.queryForObject("SELECT status FROM push_deliveries WHERE notification_id=?",String.class,id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE push_token=?",Long.class,token)).isZero();
    }
    @Test void boundedRetriesReadCancellationAndRollback() throws Exception {
        var a=session();String token="ExpoPushToken["+UUID.randomUUID()+"]";
        mvc.perform(device(a,token)).andExpect(status().isNoContent());
        UUID read=note(a);
        mvc.perform(put("/api/me/notifications/"+read+"/read").header("Authorization","Bearer "+a.path("accessToken").asText())).andExpect(status().isNoContent());
        processor.process();org.mockito.Mockito.verify(gateway,never()).send(token,"/bildirimler");
        UUID id=note(a);
        when(gateway.send(token,"/bildirimler")).thenReturn(new PushGateway.Result("RETRY",null,"PROVIDER_UNAVAILABLE"));
        for(int i=0;i<6;i++) {jdbc.update("UPDATE push_deliveries SET due_at=CURRENT_TIMESTAMP WHERE notification_id=?",id);processor.process();}
        assertThat(jdbc.queryForObject("SELECT status FROM push_deliveries WHERE notification_id=?",String.class,id)).isEqualTo("FAILED");
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        UUID rollback=tx.execute(status->{UUID value=note(a);status.setRollbackOnly();return value;});
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_deliveries WHERE notification_id=?",Long.class,rollback)).isZero();
    }
    @Test void tokenRotationCancelsOldReceiptsWithoutDisablingTheNewToken() throws Exception {
        var a=session();String old="ExpoPushToken["+UUID.randomUUID()+"]";String next="ExpoPushToken["+UUID.randomUUID()+"]";
        mvc.perform(device(a,old)).andExpect(status().isNoContent());UUID id=note(a);
        when(gateway.send(old,"/bildirimler")).thenReturn(new PushGateway.Result("TICKET","old-ticket",null));
        processor.process();mvc.perform(device(a,next)).andExpect(status().isNoContent());
        jdbc.update("UPDATE push_deliveries SET due_at=CURRENT_TIMESTAMP WHERE notification_id=?",id);processor.process();
        assertThat(jdbc.queryForObject("SELECT status FROM push_deliveries WHERE notification_id=?",String.class,id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE push_token=?",Long.class,next)).isEqualTo(1);
        org.mockito.Mockito.verify(gateway,never()).receipt("old-ticket");
    }
    @Test void lateProviderFailureCannotDisableAReenabledDevice() throws Exception {
        var a=session();String token="ExpoPushToken["+UUID.randomUUID()+"]";
        mvc.perform(device(a,token)).andExpect(status().isNoContent());note(a);
        when(gateway.send(token,"/bildirimler")).thenAnswer(invocation -> {
            mvc.perform(delete("/api/me/push-device").header("Authorization","Bearer "+a.path("accessToken").asText())).andExpect(status().isNoContent());
            mvc.perform(device(a,token)).andExpect(status().isNoContent());
            return new PushGateway.Result("FAILED",null,"DEVICE_NOT_REGISTERED");
        });
        processor.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_devices WHERE push_token=?",Long.class,token)).isEqualTo(1);
    }
    @Test void exportsMobileContract() throws Exception {
        String json=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/mobile-openapi.json"),json);
    }
}
