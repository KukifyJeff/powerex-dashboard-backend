package com.chng.powerexdashboardbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.import-data")
public class ImportDataProperties {
    private String adminPasswordHash;
}
