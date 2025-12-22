package com.irrigation.dashboardapi.config;

import core.rmi.RmiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RmiConfig {

    @Bean
    public RmiClient rmiClient() {
        try {
            String rmiHost = "127.0.0.1";
            int rmiPort = 1099;
            String rmiName = "IrrigationService";
            return new RmiClient(rmiHost, rmiPort, rmiName);
        } catch (Exception e) {
            // fail fast: if RMI cannot be created, dashboard-api should not start
            throw new IllegalStateException("Failed to create RmiClient", e);
        }
    }
}
