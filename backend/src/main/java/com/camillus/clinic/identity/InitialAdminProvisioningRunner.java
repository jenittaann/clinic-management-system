package com.camillus.clinic.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "camillus.bootstrap-admin", name = "enabled", havingValue = "true")
class InitialAdminProvisioningRunner implements ApplicationRunner {
    private final InitialAdminProvisioningService provisioning;
    private final String email;
    private final String password;

    InitialAdminProvisioningRunner(InitialAdminProvisioningService provisioning,
                                   @Value("${camillus.bootstrap-admin.email:}") String email,
                                   @Value("${camillus.bootstrap-admin.password:}") String password) {
        this.provisioning = provisioning;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisioning.provision(email, password);
    }
}
