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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.LongStream;

@Slf4j
@RestController
@RequestMapping("/api/v1.0/event")
public class EventController {

    @Value("${event.statistics.expiry:60s}")
    private Duration statisticsExpiry;

    private final AsyncRabbitTemplate asyncRabbitTemplate;
    private final DirectExchange fastEventExchange;
    private final DirectExchange slowEventExchange;

    private final Timer fastCurrentTimer;
    private final Timer slowCurrentTimer;

    private final Timer slowExecutionHistogram;
    private final Timer fastExecutionHistogram;

    @Autowired
    public EventController(AsyncRabbitTemplate rabbitTemplate, @Qualifier("fastEventExchange") DirectExchange fastEventExchange,
                           @Qualifier("slowEventExchange") DirectExchange slowEventExchange, MeterRegistry meterRegistry) {
        this.asyncRabbitTemplate = rabbitTemplate;
        this.fastEventExchange = fastEventExchange;
        this.slowEventExchange = slowEventExchange;
        this.slowExecutionHistogram = Timer
                .builder("slowExecutionTime")
                //.publishPercentileHistogram(true)
                .distributionStatisticExpiry(statisticsExpiry)
                .sla(LongStream.range(1, 20).map(l ->  l * 100).mapToObj(Duration::ofMillis).toArray(Duration[]::new))
                .register(meterRegistry);

        this.fastExecutionHistogram =  Timer
                .builder("fastExecutionTime")
                .sla(LongStream.range(1, 20).map(l ->  l * 100).mapToObj(Duration::ofMillis).toArray(Duration[]::new))
                //.publishPercentileHistogram(true)
                .distributionStatisticExpiry(statisticsExpiry)
                .register(meterRegistry);
        this.fastCurrentTimer = Timer.builder("fastExecutionTimer")
                .publishPercentiles(0.95)
                .distributionStatisticBufferLength(3)
                .distributionStatisticExpiry(Duration.ofSeconds(20))
                .register(meterRegistry);
        this.slowCurrentTimer = Timer.builder("slowExecutionTimer")
                .publishPercentiles(0.95)
                .distributionStatisticBufferLength(3)
                .distributionStatisticExpiry(Duration.ofSeconds(20))
                .register(meterRegistry);

    }


    @PostMapping("slow")
    ResponseEntity slowEvent(@RequestParam("text") String text, @RequestParam Integer multiply) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Event/slow received text = {}", text);
        EventMessage eventMessage = EventMessage.create(text, multiply);
        ListenableFuture<EventMessage> future = asyncRabbitTemplate.convertSendAndReceiveAsType(slowEventExchange.getName(),"rpc", eventMessage, new ParameterizedTypeReference<EventMessage>() {});
        EventMessage result = future.get(120, TimeUnit.SECONDS);
        log.info("Event/slow result = {} status = {}", result, result!= null ? result.getResultStatus(): "UNKNOWN");
        if(result != null) {
            Duration executionTime = Duration.between(result.getOnCreateTime(), result.getOnEndProcess());
            long executionTimeMillis = executionTime.toMillis();
            log.info("Slow execution time millis = {}", executionTimeMillis);
            slowExecutionHistogram.record(executionTime);
            slowCurrentTimer.record(executionTime);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @PostMapping("fast")
    ResponseEntity fastEvent(@RequestParam("text") String text, @RequestParam Integer multiply) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Event/fast received text = {}", text);
        EventMessage eventMessage = EventMessage.create(text, multiply);
        ListenableFuture<EventMessage> future = asyncRabbitTemplate.convertSendAndReceiveAsType(fastEventExchange.getName(),"rpc", eventMessage, new ParameterizedTypeReference<EventMessage>() {});
        EventMessage result = future.get(120, TimeUnit.SECONDS);
        log.info("Event/fast result = {} status = {}", result, result!= null ? result.getResultStatus() : "UNKNOWN");
        if(result != null) {
            Duration executionTime = Duration.between(result.getOnCreateTime(), result.getOnEndProcess());
            long executionTimeMillis = executionTime.toMillis();
            log.info("Fast execution time millis = {}", executionTimeMillis);
            fastExecutionHistogram.record(executionTime);
            fastCurrentTimer.record(executionTime);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @GetMapping("stats")
    Map<String, Object>  getStatisctics() {
        Map<String, Object> summaries =  new HashMap<>();
        summaries.put("fast", fastExecutionHistogram.takeSnapshot().toString());
        summaries.put("slow", slowExecutionHistogram.takeSnapshot().toString());
        return summaries;
    }
}
