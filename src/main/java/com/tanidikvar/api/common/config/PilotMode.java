package com.tanidikvar.api.common.config;

import com.tanidikvar.api.common.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Temporary restrictions for the free pilot, where persistent private file storage is unavailable. */
@Component
public class PilotMode {
    private final boolean enabled;

    public PilotMode(@Value("${app.pilot-mode:false}") boolean enabled) { this.enabled=enabled; }
    public boolean enabled() { return enabled; }
    public void requireFileUploadsEnabled() {
        if(enabled) throw new DomainException(409,"PILOT_RESTRICTION","Pilot sürümünde dosya yükleme ve yönetici başvuruları henüz açık değil.");
    }
}
