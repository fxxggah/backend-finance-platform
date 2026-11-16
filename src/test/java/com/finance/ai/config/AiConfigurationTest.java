package com.finance.ai.config;

import com.finance.ai.controller.TransactionDraftController;
import com.finance.ai.service.TransactionDraftService;
import com.finance.account.repository.AccountRepository;
import com.finance.category.repository.CategoryRepository;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiConfigurationTest {
    private ApplicationContextRunner runner(String... profiles) {
        return new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withUserConfiguration(AiConfiguration.class, TransactionDraftController.class)
                .withBean(AccountRepository.class, () -> mock(AccountRepository.class))
                .withBean(CategoryRepository.class, () -> mock(CategoryRepository.class))
                .withBean(CreditCardRepository.class, () -> mock(CreditCardRepository.class))
                .withBean(com.finance.ai.service.AiQuotaService.class, () -> mock(com.finance.ai.service.AiQuotaService.class))
                .withBean(CurrentUser.class, CurrentUser::new).withBean(Clock.class, Clock::systemUTC);
    }

    @Test void defaultDisabledEvenInDev() {
        runner("dev").run(context -> assertThat(context).doesNotHaveBean(TransactionDraftController.class)
                .doesNotHaveBean(TransactionDraftService.class));
    }

    @Test void availableOnlyWithDevAndExplicitFlag() {
        runner("dev").withPropertyValues("app.ai.enabled=true").run(context -> assertThat(context)
                .hasSingleBean(TransactionDraftController.class).hasSingleBean(TransactionDraftService.class));
    }

    @Test void prodAndMixedProfilesNeverExposeFeatureEvenWithFlag() {
        for (String[] profiles : new String[][]{{"prod"}, {"dev", "prod"}, {"test"}}) {
            runner(profiles).withPropertyValues("app.ai.enabled=true").run(context -> assertThat(context)
                    .doesNotHaveBean(TransactionDraftController.class).doesNotHaveBean(TransactionDraftService.class));
        }
    }
}
