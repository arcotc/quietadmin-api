package uk.co.quietadmin.api.billing;

import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.QaGroup;
import uk.co.quietadmin.domain.group.QaGroupRepository;
import uk.co.quietadmin.domain.group.StripeSubscriptionStatus;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final StripeCheckoutService stripeCheckoutService;
    private final CurrentUserService currentUserService;
    private final QaGroupRepository groupRepository;

    @Value("${app.ui.base-url}")
    private String uiBaseUrl;

    /* ======================================================
       START TRIAL (SIGNUP FLOW)
       ====================================================== */

    @PostMapping("/start-trial")
    public ResponseEntity<Map<String, String>> startTrial(@Valid @RequestBody CreateCheckoutSessionRequest req) {
        String url = stripeCheckoutService.createTrialCheckoutSession(req);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /* ======================================================
       SUBSCRIPTION STATUS
       ====================================================== */

    @GetMapping("/status")
    public ResponseEntity<BillingStatusResponse> status(Principal principal) {

        Long groupId = currentUserService.getCurrentGroupId(principal.getName());
        QaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        StripeSubscriptionStatus raw = group.getStripeSubscriptionStatus();
        String statusStr = raw != null ? raw.name().toLowerCase() : "none";
        String planName = (raw == StripeSubscriptionStatus.TRIALING) ? "Standard (Trial)" : "Standard";

        return ResponseEntity.ok(new BillingStatusResponse(
                statusStr,
                planName,
                group.getStripeTrialEnd(),
                group.getStripeCurrentPeriodEnd(),
                group.getStripeCancelAt()
        ));
    }

    /* ======================================================
       STRIPE BILLING PORTAL REDIRECT
       ====================================================== */

    @GetMapping("/portal-session")
    public ResponseEntity<Map<String, String>> portalSession(Principal principal) {

        Long groupId = currentUserService.getCurrentGroupId(principal.getName());
        QaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        if (group.getStripeCustomerId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "No billing account linked to this group."));
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(group.getStripeCustomerId())
                    .setReturnUrl(uiBaseUrl + "/billing")
                    .build();

            Session session = Session.create(params);
            return ResponseEntity.ok(Map.of("url", session.getUrl()));

        } catch (StripeException e) {
            throw new RuntimeException("Failed to create billing portal session", e);
        }
    }

    /* ======================================================
       RESPONSE RECORD
       ====================================================== */

    public record BillingStatusResponse(
            String status,
            String planName,
            Instant trialEnd,
            Instant currentPeriodEnd,
            Instant cancelAt
    ) {}
}
