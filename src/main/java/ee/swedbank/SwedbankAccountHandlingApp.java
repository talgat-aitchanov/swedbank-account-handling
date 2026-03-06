package ee.swedbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SwedbankAccountHandlingApp {
    public static void main(String[] args) {
        SpringApplication.run(SwedbankAccountHandlingApp.class, args);
    }
}
