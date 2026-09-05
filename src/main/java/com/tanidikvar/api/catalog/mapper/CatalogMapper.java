package com.tanidikvar.api.catalog.mapper;
import com.tanidikvar.api.catalog.entity.CatalogEntry;
import com.tanidikvar.api.catalog.dto.CatalogResponse;
import org.springframework.stereotype.Component;
@Component
public class CatalogMapper {
    public CatalogResponse toResponse(CatalogEntry entry) { return new CatalogResponse(entry.id(),entry.name(),entry.deletedAt(),entry.version()); }
}
