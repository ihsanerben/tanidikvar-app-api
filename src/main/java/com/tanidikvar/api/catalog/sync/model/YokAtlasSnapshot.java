package com.tanidikvar.api.catalog.sync.model;

import java.time.Instant;
import java.util.List;

public record YokAtlasSnapshot(
        String checksum,
        Instant sourceUpdatedAt,
        List<YokAtlasProgram> programs) { }
