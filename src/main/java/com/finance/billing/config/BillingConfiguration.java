package com.finance.billing.config;

import com.finance.billing.provider.*;
import com.finance.billing.service.*;
import com.finance.user.service.UserDataDeletionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(BillingProperties.class)
public class BillingConfiguration {
    @Configuration(proxyBeanMethods=false) @Profile("dev & !prod")
    @ConditionalOnProperty(prefix="app.billing", name="enabled", havingValue="true") @EnableScheduling
    static class Sandbox {
        @Bean BillingGateway billingGateway(BillingProperties properties, Clock clock) { return new StripeBillingGateway(properties, clock); }
        @Bean BillingService billingService(BillingStore store, BillingGateway gateway, BillingProperties properties, UserDataDeletionService deletion, Clock clock) {
            return new BillingService(store, gateway, properties, deletion, clock);
        }
        @Bean @ConditionalOnProperty(prefix="app.billing", name="worker-enabled", havingValue="true", matchIfMissing=true)
        BillingWorker billingWorker(BillingStore store, BillingService service) { return new BillingWorker(store, service); }
    }
}
