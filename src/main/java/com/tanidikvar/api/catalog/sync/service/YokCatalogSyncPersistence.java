package com.tanidikvar.api.catalog.sync.service;

import com.tanidikvar.api.catalog.sync.dto.YokCatalogSyncResponse;
import com.tanidikvar.api.catalog.sync.model.YokAtlasSnapshot;
import com.tanidikvar.api.catalog.sync.repository.YokCatalogSyncRepository;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YokCatalogSyncPersistence {
    private final YokCatalogSyncRepository repository;
    public YokCatalogSyncPersistence(YokCatalogSyncRepository repository){this.repository=repository;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public YokCatalogSyncResponse start(UUID actor,String operation){
        UUID id=UUID.randomUUID();repository.start(id,actor,operation);return get(id);
    }

    @Transactional(readOnly=true)
    public YokCatalogSyncResponse get(UUID id){return repository.find(id).orElseThrow(()->new DomainException(404,"NOT_FOUND","Senkronizasyon kaydı bulunamadı."));}

    @Transactional
    public void apply(UUID id,YokAtlasSnapshot snapshot){
        if(!"STARTED".equals(repository.lockStatus(id)))return;
        if(repository.successfulSnapshotExists(snapshot.checksum())){
            int universities=(int)snapshot.programs().stream().map(p->p.universityId()).distinct().count();
            repository.skip(id,snapshot,universities);return;
        }
        repository.apply(id,snapshot);
    }

    @Transactional
    public void preview(UUID id,YokAtlasSnapshot snapshot){
        if(!"STARTED".equals(repository.lockStatus(id)))return;
        repository.preview(id,snapshot);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void fail(UUID id,String reason){repository.fail(id,reason);}
}
