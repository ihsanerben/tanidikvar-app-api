package com.tanidikvar.api.catalog.sync.service;

import com.tanidikvar.api.catalog.sync.dto.YokCatalogSyncResponse;
import com.tanidikvar.api.catalog.sync.model.YokAtlasSnapshot;
import com.tanidikvar.api.catalog.sync.model.YokAtlasFetchResult;
import com.tanidikvar.api.catalog.sync.model.YokAtlasProgram;
import com.tanidikvar.api.catalog.sync.model.YokAtlasNetStats;
import java.time.Duration;
import java.time.Instant;
import com.tanidikvar.api.catalog.sync.repository.YokCatalogSyncRepository;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YokCatalogSyncPersistence {
    private final YokCatalogSyncRepository repository;
    private final Duration staleAfter;
    public YokCatalogSyncPersistence(YokCatalogSyncRepository repository,
            @org.springframework.beans.factory.annotation.Value("${app.catalog.yok-atlas-stale-after:6h}") Duration staleAfter){this.repository=repository;this.staleAfter=staleAfter;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public YokCatalogSyncResponse start(UUID actor,String operation){
        repository.failStaleRuns(Instant.now().minus(staleAfter));
        UUID id=UUID.randomUUID();repository.start(id,actor,operation);return get(id);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void stagePrograms(UUID id,int offset,List<YokAtlasProgram> rows){repository.stagePrograms(id,offset,rows);}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void stageNets(UUID id,int offset,List<YokAtlasNetStats> rows){repository.stageNets(id,offset,rows);}

    @Transactional
    public void finish(UUID id,String operation,YokAtlasFetchResult result){
        if(!"STARTED".equals(repository.lockStatus(id)))return;
        if("APPLY".equals(operation)&&repository.successfulSnapshotExists(result.checksum())){
            repository.skipStaged(id,result);return;
        }
        if("PREVIEW".equals(operation))repository.previewStaged(id,result);else repository.applyStaged(id,result);
    }

    @Transactional(readOnly=true)
    public YokCatalogSyncResponse get(UUID id){return repository.find(id).orElseThrow(()->new DomainException(404,"NOT_FOUND","Senkronizasyon kaydı bulunamadı."));}
    @Transactional(readOnly=true) public List<YokCatalogSyncResponse> history(){return repository.history(50);}

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
    public void fail(UUID id,String reason){repository.fail(id,reason);repository.clearStage(id);}
}
