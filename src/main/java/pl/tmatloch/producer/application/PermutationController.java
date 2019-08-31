package pl.tmatloch.producer.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1.0/")
public class PermutationController {

    private final RabbitTemplate rabbitTemplate;
    private final DirectExchange directExchange;

    @Autowired
    public PermutationController(RabbitTemplate rabbitTemplate, DirectExchange directExchange) {
        rabbitTemplate.setReplyTimeout(20000);
        this.rabbitTemplate = rabbitTemplate;
        this.directExchange = directExchange;
    }


    @GetMapping("all-permutations")
    List<String> allPermutiations(@RequestParam("text") String text, @RequestParam Integer limit){
        log.info("allPermutiations received text = {}", text);
        List<String> result = (List<String>) rabbitTemplate.convertSendAndReceive(directExchange.getName(),"rpc", text);
        log.info("allPermutiations result count = {}", result != null ? result.size(): "UNKNOWN");

        if(limit != null && result != null && result.size() > limit){
            return result.subList(0,   limit - 1);
        }
        return result;
    }
}
