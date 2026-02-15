package uk.co.quietadmin.api.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    @Value("${stripe.webhookSecret}")
    private String webhookSecret;

    private final StripeWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader,
            HttpServletRequest request
    ) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret); // verify  [oai_citation:7‡Stripe Docs](https://docs.stripe.com/webhooks/signature?locale=en-GB&utm_source=chatgpt.com)
            webhookService.process(event, payload);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(400).body("invalid signature");
        } catch (Exception e) {
            // Returning 500 tells Stripe to retry (good if transient)
            return ResponseEntity.status(500).body("error");
        }
    }
}