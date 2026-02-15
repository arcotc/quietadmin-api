package uk.co.quietadmin.api.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final StripeCheckoutService stripeCheckoutService;

    @PostMapping("/start-trial")
    public ResponseEntity<Map<String, String>> startTrial(@RequestBody CreateCheckoutSessionRequest req) {
        String url = stripeCheckoutService.createTrialCheckoutSession(req);
        return ResponseEntity.ok(Map.of("url", url));
    }
}