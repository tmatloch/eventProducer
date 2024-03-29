package pl.tmatloch.producer.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@ToString(exclude = "result")
@NoArgsConstructor
public class EventMessage {

    String id;
    Instant onCreateTime;
    Instant onStartProcess;
    Instant onEndProcess;

    String processData;
    Integer multiply;
    String resultStatus;

    public static EventMessage create(String dataToProcess, Integer multiply) {
        String id = UUID.randomUUID().toString();
        Instant instant = Instant.now();
        return new EventMessage(id, instant, null, null, dataToProcess, multiply, null);
    }
}
