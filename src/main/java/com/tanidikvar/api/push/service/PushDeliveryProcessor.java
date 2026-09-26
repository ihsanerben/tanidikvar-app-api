package com.tanidikvar.api.push.service;

import com.tanidikvar.api.push.repository.PushDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PushDeliveryProcessor {
    private static final Logger log=LoggerFactory.getLogger(PushDeliveryProcessor.class);
    private final PushDeliveryRepository deliveries;
    private final PushGateway gateway;
    private final PushDeliveryFinalizer finalizer;
    private final boolean enabled;
    public PushDeliveryProcessor(PushDeliveryRepository deliveries,PushGateway gateway,PushDeliveryFinalizer finalizer,@Value("${app.push.enabled:false}") boolean enabled) {
        this.deliveries=deliveries;this.gateway=gateway;this.finalizer=finalizer;this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${app.push.delay-ms:30000}",initialDelayString="${app.push.initial-delay-ms:60000}")
    public void scheduled() {
        if(!enabled) return;
        try { process(); } catch(RuntimeException exception) { log.warn("push_cycle_failed type={}",exception.getClass().getSimpleName()); }
    }
    // No transaction spans provider I/O; database claims commit before network calls.
    public void process() {
        deliveries.expire();
        for(var delivery:deliveries.claim()) {
            var token=deliveries.eligibleToken(delivery);
            if(token.isEmpty()) { finalizer.finish(delivery,"CANCELLED",null,null,null); continue; }
            PushGateway.Result result;
            try { result=delivery.ticket()==null?gateway.send(token.get().token(),token.get().url()):gateway.receipt(delivery.ticket()); }
            catch(RuntimeException exception) { result=new PushGateway.Result("RETRY",null,"PROVIDER_UNAVAILABLE"); }
            String state=result.state().equals("RETRY")?(delivery.attempt()>=6?"FAILED":delivery.ticket()==null?"PENDING":"TICKET"):result.state();
            finalizer.finish(delivery,state,result.ticket(),result.error(),token.get().token());
            if(state.equals("FAILED")) log.warn("push_delivery_failed delivery={} code={}",delivery.id(),result.error());
        }
    }
}
