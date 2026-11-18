package com.finance.billing;
import com.finance.billing.config.BillingConfiguration;
import com.finance.billing.provider.BillingGateway;
import com.finance.billing.service.*;
import com.finance.user.service.UserDataDeletionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BillingConfigurationTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner().withUserConfiguration(BillingConfiguration.class)
                .withBean(Clock.class,Clock::systemUTC).withBean(BillingStore.class,()->mock(BillingStore.class))
                .withBean(UserDataDeletionService.class,()->mock(UserDataDeletionService.class))
                .withPropertyValues("spring.profiles.active="+profile,"app.billing.worker-enabled=false",
                        "app.billing.secret-key=sk_test_fixture", "app.billing.webhook-secret=whsec_fixture",
                        "app.billing.price-id=price_fixture","app.billing.portal-configuration-id=bpc_fixture");
    }
    @Test void disabledByDefaultAndAlwaysAbsentInProductionIncludingMixedProfiles() {
        runner("dev").run(c->assertThat(c).doesNotHaveBean(BillingService.class));
        for(String profile:new String[]{"prod","dev,prod"}) runner(profile).withPropertyValues("app.billing.enabled=true")
                .run(c->assertThat(c).doesNotHaveBean(BillingGateway.class));
    }
    @Test void devOptInUsesOnlyTestCredentialsAndCanDisableWorkerForTests() {
        runner("dev").withPropertyValues("app.billing.enabled=true").run(c-> {
            assertThat(c).hasSingleBean(BillingGateway.class).hasSingleBean(BillingService.class).doesNotHaveBean(BillingWorker.class);
        });
        runner("dev").withPropertyValues("app.billing.enabled=true","app.billing.secret-key=sk_live_fixture")
                .run(c->assertThat(c).hasFailed());
    }
}
