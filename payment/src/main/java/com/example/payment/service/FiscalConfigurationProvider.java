package com.example.payment.service;

public interface FiscalConfigurationProvider {

    FiscalConfiguration getActiveFiscalConfiguration();

    record FiscalConfiguration(int taxSystemCode, int vatCode) {
    }
}
