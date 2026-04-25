package com.example.api.admincms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogueImportJobRepository extends JpaRepository<CatalogueImportJob, UUID> {
    List<CatalogueImportJob> findTop25ByOrderByCreatedAtDesc();
}
