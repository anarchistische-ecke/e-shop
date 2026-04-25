package com.example.api.admincms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogueImportRowRepository extends JpaRepository<CatalogueImportRow, UUID> {
    List<CatalogueImportRow> findByJobIdOrderByRowNumberAsc(UUID jobId);
}
