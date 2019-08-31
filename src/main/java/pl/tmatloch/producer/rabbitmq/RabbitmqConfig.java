package pl.tmatloch.producer.rabbitmq;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitmqConfig {


    @Bean
    public DirectExchange exchange() {
        return new DirectExchange("permutation.rpc");
    }
}
