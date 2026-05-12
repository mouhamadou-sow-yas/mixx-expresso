package sn.mixx.expresso.web.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.service.dto.response.ApiResponse;
import sn.mixx.expresso.service.dto.response.PagedResponse;
import sn.mixx.expresso.service.dto.response.TransactionResponse;
import sn.mixx.expresso.service.transaction.TransactionService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final TransactionService transactionService;

    @GetMapping("/{msisdn}/transactions")
    public Mono<ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>>> getClientHistory(
        @PathVariable String msisdn,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        Instant fromInstant = from != null
            ? LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant()
            : Instant.now().minusSeconds(30L * 24 * 3600);

        Instant toInstant = to != null
            ? LocalDate.parse(to).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
            : Instant.now();

        return transactionService.getClientHistory(msisdn, status, type, fromInstant, toInstant, page, size)
            .map(paged -> ResponseEntity.ok(ApiResponse.success(paged)));
    }
}