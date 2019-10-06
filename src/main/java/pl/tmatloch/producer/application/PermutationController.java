package pl.tmatloch.producer.application;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
@RequestMapping("/api/v1.0/")
public class PermutationController {

    private final AsyncRabbitTemplate asyncRabbitTemplate;
    private final DirectExchange fastPermutationExchange;
    private final DirectExchange slowPermutationExchange;

    private final Timer fastTimer;
    private final Timer slowTimer;

    private final DistributionSummary slowExecutionTime;
    private final DistributionSummary fastExecutionTime;

    @Autowired
    public PermutationController(AsyncRabbitTemplate rabbitTemplate, @Qualifier("fastPermutationExchange") DirectExchange fastPermutationExchange,
                                 @Qualifier("slowPermutationExchange") DirectExchange slowPermutationExchange, MeterRegistry meterRegistry) {
        this.asyncRabbitTemplate = rabbitTemplate;
        this.fastPermutationExchange = fastPermutationExchange;
        this.slowPermutationExchange = slowPermutationExchange;
        this.slowExecutionTime = DistributionSummary
                .builder("slowExecutionTime")
                .baseUnit("millis")
                .register(meterRegistry);

        this.fastExecutionTime =  DistributionSummary
                .builder("fastExecutionTime")
                .baseUnit("millis")
                .register(meterRegistry);
        this.fastTimer = Timer.builder("fastExecutionTimer")
                .publishPercentileHistogram()
                .distributionStatisticExpiry(Duration.ofSeconds(20))
                .register(meterRegistry);
        this.slowTimer = Timer.builder("slowExecutionTimer")
                .publishPercentileHistogram()
                .distributionStatisticExpiry(Duration.ofSeconds(20))
                .register(meterRegistry);

    }


    @GetMapping("slow/permutation")
    List<String> slowPermutation(@RequestParam("text") String text, @RequestParam Integer limit) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("slow/Permutation received text = {}", text);
        PermutationMessage permutationMessage = PermutationMessage.create(text);
        ListenableFuture<PermutationMessage> future = asyncRabbitTemplate.convertSendAndReceiveAsType(slowPermutationExchange.getName(),"rpc", permutationMessage, new ParameterizedTypeReference<PermutationMessage>() {});
        PermutationMessage result = future.get(120, TimeUnit.SECONDS);
        log.info("slow/Permutation result count = {}", result!= null ? result.getResult().size(): "UNKNOWN");
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

    @GetMapping("fast/permutation")
    List<String> fastPermutation(@RequestParam("text") String text, @RequestParam Integer limit) throws InterruptedException, ExecutionException, TimeoutException {
        log.info("fast/permutation received text = {}", text);
        PermutationMessage permutationMessage = PermutationMessage.create(text);
        ListenableFuture<PermutationMessage> future = asyncRabbitTemplate.convertSendAndReceiveAsType(fastPermutationExchange.getName(),"rpc", permutationMessage, new ParameterizedTypeReference<PermutationMessage>() {});
        PermutationMessage result = future.get(120, TimeUnit.SECONDS);
        log.info("fast/Permutation result = {} count = {}", result, result!= null ? result.getResult().size(): "UNKNOWN");
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
