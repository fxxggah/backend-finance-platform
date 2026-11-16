package com.finance.ai.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated @ConfigurationProperties("app.ai.quota")
public class AiQuotaProperties {
    @Min(1) private int userSuccessesPerDay = 10;
    @Min(1) private int globalAttemptsPerMinute = 10;
    @Min(1) private int globalAttemptsPerDay = 400;
    @Min(60) private int leaseSeconds = 120;
    @Min(1) private int providerCooldownSeconds = 60;
}
