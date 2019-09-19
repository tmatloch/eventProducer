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
public class PermutationMessage {

    String id;
    Instant onCreateTime;
    Instant onStartProcess;
    Instant onEndProcess;

    String processData;
    List<String> result;

    public static PermutationMessage create(String dataToProcess) {
        String id = UUID.randomUUID().toString();
        Instant instant = Instant.now();
        return new PermutationMessage(id, instant, null, null, dataToProcess, null);
    }
}
