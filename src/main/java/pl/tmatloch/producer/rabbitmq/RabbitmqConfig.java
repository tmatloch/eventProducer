package pl.tmatloch.producer.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitmqConfig {


    @Bean("slowPermutationExchange")
    public DirectExchange slowPermutationExchange() {
        return new DirectExchange("slow.permutation.rpc");
    }

    @Bean("fastPermutationExchange")
    public DirectExchange fastPermutationExchange() {
        return new DirectExchange("fast.permutation.rpc");
    }

    @Bean
    MessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter());
        template.setReplyTimeout(20000);
        return template;
    }
}
