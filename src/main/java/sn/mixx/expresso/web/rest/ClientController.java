package sn.mixx.expresso.web.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.service.dto.response.TransactionResponse;
import sn.mixx.expresso.service.transaction.TransactionService;

@RestController
@RequestMapping("/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final TransactionService transactionService;

    @GetMapping("/{msisdn}/transactions")
    public Flux<TransactionResponse> getClientHistory(@PathVariable String msisdn) {
        return transactionService.getClientHistory(msisdn);
    }
}