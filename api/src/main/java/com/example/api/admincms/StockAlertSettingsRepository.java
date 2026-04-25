package com.example.api.admincms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockAlertSettingsRepository extends JpaRepository<StockAlertSettings, UUID> {
}
