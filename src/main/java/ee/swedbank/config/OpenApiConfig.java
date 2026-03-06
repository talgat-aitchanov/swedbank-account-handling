package ee.swedbank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI swedbankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Swedbank Account Handling API")
                        .version("v1")
                        .description("Bank account handling microservice"));
    }
}

