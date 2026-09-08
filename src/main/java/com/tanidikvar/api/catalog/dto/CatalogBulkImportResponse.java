package com.tanidikvar.api.catalog.dto;

public record CatalogBulkImportResponse(int universitiesCreated,int departmentsCreated,int matchesCreated,int skipped) { }
