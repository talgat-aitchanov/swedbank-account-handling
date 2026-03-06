package ee.swedbank.config;

import lombok.extern.slf4j.Slf4j;
import org.h2.tools.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Slf4j
@Configuration
public class H2ServerConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(name = "h2.tcp.enabled", havingValue = "true", matchIfMissing = true)
    public Server h2TcpServer() throws SQLException {
        log.info("Starting H2 TCP server on port 9092");
        return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092");
    }
}
