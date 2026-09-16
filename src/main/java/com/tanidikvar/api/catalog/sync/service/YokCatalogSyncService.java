package com.tanidikvar.api.catalog.sync.service;

import com.tanidikvar.api.catalog.sync.client.YokAtlasClient;
import com.tanidikvar.api.catalog.sync.dto.YokCatalogSyncResponse;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class YokCatalogSyncService {
    private static final Logger log=LoggerFactory.getLogger(YokCatalogSyncService.class);
    private final YokAtlasClient client;
    private final YokCatalogSyncPersistence persistence;
    private final ThreadPoolTaskExecutor executor;

    public YokCatalogSyncService(YokAtlasClient client,YokCatalogSyncPersistence persistence,
            @Qualifier("yokCatalogSyncExecutor") ThreadPoolTaskExecutor executor){
        this.client=client;this.persistence=persistence;this.executor=executor;
    }

    public YokCatalogSyncResponse start(UUID actor){
        return schedule(actor,"APPLY");
    }

    public YokCatalogSyncResponse preview(UUID actor){return schedule(actor,"PREVIEW");}

    private YokCatalogSyncResponse schedule(UUID actor,String operation){
        YokCatalogSyncResponse run=persistence.start(actor,operation);
        try{executor.execute(()->execute(run.id(),operation));}
        catch(RuntimeException exception){persistence.fail(run.id(),"Başka bir katalog senkronizasyonu çalışıyor.");throw new DomainException(409,"CATALOG_SYNC_RUNNING","Başka bir katalog senkronizasyonu çalışıyor.");}
        return run;
    }

    public YokCatalogSyncResponse get(UUID id){return persistence.get(id);}
    public List<YokCatalogSyncResponse> history(){return persistence.history();}

    private void execute(UUID id,String operation){
        try{
            var snapshot=client.fetchCompleteSnapshot();
            if("PREVIEW".equals(operation))persistence.preview(id,snapshot);else persistence.apply(id,snapshot);
            log.info("dataset_catalog_sync_completed runId={} operation={} options={}",id,operation,snapshot.programs().size());
        }catch(RuntimeException exception){
            String reason=exception instanceof IllegalStateException&&exception.getMessage()!=null
                    ?exception.getMessage():"Program veri setine erişilemedi.";
            persistence.fail(id,reason.length()>2000?reason.substring(0,2000):reason);
            log.warn("dataset_catalog_sync_failed runId={} type={}",id,exception.getClass().getSimpleName());
        }
    }
}
