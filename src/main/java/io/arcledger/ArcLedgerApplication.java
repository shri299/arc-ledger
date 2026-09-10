package io.arcledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArcLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArcLedgerApplication.class, args);
    }
}
