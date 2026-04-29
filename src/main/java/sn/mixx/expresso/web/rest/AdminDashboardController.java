package sn.mixx.expresso.web.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.service.backoffice.DashboardService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getDashboard() {
        return dashboardService.getDashboard().map(ResponseEntity::ok);
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refreshDashboard() {
        return dashboardService.refreshDashboard().map(ResponseEntity::ok);
    }
}