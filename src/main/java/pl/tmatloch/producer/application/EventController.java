package pl.tmatloch.producer.application;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/v1.0/event")
public class EventController {

    @Value("${event.statistics.expiry:60s}")
    private Duration statisticsExpiry;

    private final AsyncRabbitTemplate asyncRabbitTemplate;
    private final DirectExchange fastEventExchange;
    private final DirectExchange slowEventExchange;

    private final Timer fastTimer;
    private final Timer slowTimer;

    private final DistributionSummary slowExecutionTime;
    private final DistributionSummary fastExecutionTime;

    @Autowired
    public EventController(AsyncRabbitTemplate rabbitTemplate, @Qualifier("fastEventExchange") DirectExchange fastEventExchange,
                           @Qualifier("slowEventExchange") DirectExchange slowEventExchange, MeterRegistry meterRegistry) {
        this.asyncRabbitTemplate = rabbitTemplate;
        this.fastEventExchange = fastEventExchange;
        this.slowEventExchange = slowEventExchange;
        this.slowExecutionTime = DistributionSummary
                .builder("slowExecutionTime")
                .publishPercentileHistogram(true)
                .distributionStatisticExpiry(statisticsExpiry)
                .baseUnit("millis")
                .register(meterRegistry);

        this.fastExecutionTime =  DistributionSummary
                .builder("fastExecutionTime")
                .publishPercentileHistogram(true)
                .distributionStatisticExpiry(statisticsExpiry)
                .baseUnit("millis")
                .register(meterRegistry);
        this.fastTimer = Timer.builder("fastExecutionTimer")
                .publishPercentiles(0.95)
                .distributionStatisticBufferLength(3)
                .distributionStatisticExpiry(Duration.ofSeconds(20))
                .register(meterRegistry);
        this.slowTimer = Timer.builder("slowExecutionTimer")
                .publishPercentiles(0.95)
                .distributionStatisticBufferLength(3)
                .distributionStatisticExpiry(Duration.ofSeconds(20))
                .register(meterRegistry);

    }


    @GetMapping("slow")
    List<String> slowEvent(@RequestParam("text") String text, @RequestParam Integer limit) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Event/slow received text = {}", text);
        EventMessage eventMessage = EventMessage.create(text);
        ListenableFuture<EventMessage> future = asyncRabbitTemplate.convertSendAndReceiveAsType(slowEventExchange.getName(),"rpc", eventMessage, new ParameterizedTypeReference<EventMessage>() {});
        EventMessage result = future.get(120, TimeUnit.SECONDS);
        log.info("Event/slow result count = {}", result!= null ? result.getResult().size(): "UNKNOWN");
        if(limit != null && result != null && result.getResult().size() > limit){
            Duration executionTime = Duration.between(result.getOnCreateTime(), result.getOnEndProcess());
            long executionTimeMillis = executionTime.toMillis();
            log.info("Slow execution time millis = {}", executionTimeMillis);
            slowExecutionTime.record(executionTimeMillis);
            slowTimer.record(executionTime);
            return result.getResult().subList(0,   limit - 1);
        }
        return result != null ? result.getResult() : null;
    }

    @GetMapping("fast")
    List<String> fastEvent(@RequestParam("text") String text, @RequestParam Integer limit) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Event/fast received text = {}", text);
        EventMessage eventMessage = EventMessage.create(text);
        ListenableFuture<EventMessage> future = asyncRabbitTemplate.convertSendAndReceiveAsType(fastEventExchange.getName(),"rpc", eventMessage, new ParameterizedTypeReference<EventMessage>() {});
        EventMessage result = future.get(120, TimeUnit.SECONDS);
        log.info("Event/fast result = {} count = {}", result, result!= null ? result.getResult().size(): "UNKNOWN");
        if(limit != null && result != null && result.getResult().size() > limit){
            Duration executionTime = Duration.between(result.getOnCreateTime(), result.getOnEndProcess());
            long executionTimeMillis = executionTime.toMillis();
            log.info("Fast execution time millis = {}", executionTimeMillis);
            fastExecutionTime.record(executionTimeMillis);
            fastTimer.record(executionTime);
            return result.getResult().subList(0,   limit - 1);
        }
        return result != null ? result.getResult() : null;
    }
}
