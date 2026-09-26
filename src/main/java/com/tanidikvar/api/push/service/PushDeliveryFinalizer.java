package com.tanidikvar.api.push.service;

import com.tanidikvar.api.push.repository.PushDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeliveryFinalizer {
    private final PushDeliveryRepository deliveries;
    public PushDeliveryFinalizer(PushDeliveryRepository deliveries) { this.deliveries=deliveries; }
    @Transactional
    public void finish(PushDeliveryRepository.Delivery delivery,String state,String ticket,String error,String token) {
        deliveries.finish(delivery,state,ticket,error,token);
    }
}
