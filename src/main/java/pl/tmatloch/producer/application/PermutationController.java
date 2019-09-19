package pl.tmatloch.producer.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;
import sun.invoke.empty.Empty;

import java.lang.reflect.ParameterizedType;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1.0/")
public class PermutationController {

    private final RabbitTemplate rabbitTemplate;
    private final DirectExchange fastPermutationExchange;
    private final DirectExchange slowPermutationExchange;

    @Autowired
    public PermutationController(RabbitTemplate rabbitTemplate, @Qualifier("fastPermutationExchange") DirectExchange fastPermutationExchange,
                                 @Qualifier("slowPermutationExchange") DirectExchange slowPermutationExchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.fastPermutationExchange = fastPermutationExchange;
        this.slowPermutationExchange = slowPermutationExchange;
    }


    @GetMapping("slow/permutation")
    List<String> slowPermutation(@RequestParam("text") String text, @RequestParam Integer limit){
        log.info("slow/Permutation received text = {}", text);
        PermutationMessage permutationMessage = PermutationMessage.create(text);
        PermutationMessage result = rabbitTemplate.convertSendAndReceiveAsType(slowPermutationExchange.getName(),"rpc", permutationMessage, new ParameterizedTypeReference<PermutationMessage>() {});
        log.info("slow/Permutation result count = {}", result!= null ? result.getResult().size(): "UNKNOWN");
        if(limit != null && result != null && result.getResult().size() > limit){
            return result.getResult().subList(0,   limit - 1);
        }
        return result != null ? result.getResult() : null;
    }

    @GetMapping("fast/permutation")
    List<String> fastPermutation(@RequestParam("text") String text, @RequestParam Integer limit){
        log.info("fast/permutation received text = {}", text);
        PermutationMessage permutationMessage = PermutationMessage.create(text);
        PermutationMessage result = rabbitTemplate.convertSendAndReceiveAsType(fastPermutationExchange.getName(),"rpc", permutationMessage, new ParameterizedTypeReference<PermutationMessage>() {});
        log.info("fast/Permutation result = {} count = {}", result, result!= null ? result.getResult().size(): "UNKNOWN");
        if(limit != null && result != null && result.getResult().size() > limit){
            return result.getResult().subList(0,   limit - 1);
        }
        return result != null ? result.getResult() : null;
    }
}
