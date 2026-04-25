package com.example.api.admincms;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_alert_settings")
public class StockAlertSettings extends BaseEntity {

    @Column(name = "threshold", nullable = false)
    private int threshold = 5;

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
