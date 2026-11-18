package com.finance.billing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.billing") @Getter @Setter
public class BillingProperties {
    private boolean enabled;
    private String secretKey = "";
    private String webhookSecret = "";
    private String priceId = "";
    private String portalConfigurationId = "";
    private String frontendOrigin = "http://localhost:3000";
}
