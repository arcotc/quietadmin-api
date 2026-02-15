package uk.co.quietadmin.api.billing;

import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.signup.PendingSignup;
import uk.co.quietadmin.domain.signup.PendingSignupRepository;
import uk.co.quietadmin.domain.user.UserAccountRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripeCheckoutService {

    private final PendingSignupRepository pendingRepo;
    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontendBaseUrl}")
    private String frontendBaseUrl;

    @Value("${stripe.priceId}")
    private String priceId;

    @Value("${stripe.trialDays:14}")
    private long trialDays;

    @Transactional
    public String createTrialCheckoutSession(CreateCheckoutSessionRequest req) {

        String email = req.email().trim().toLowerCase();
        if (userRepo.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Create short-lived pending record
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();

        PendingSignup pending = PendingSignup.builder()
                .token(token)
                .email(email)
                .firstName(req.firstName().trim())
                .groupName(req.groupName().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .createdAt(now)
                .expiresAt(now.plus(2, ChronoUnit.HOURS))
                .build();

        pendingRepo.save(pending);

        // Stripe Checkout Session (subscription + trial)
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(frontendBaseUrl + "/signup/success?session_id={CHECKOUT_SESSION_ID}")
                        .setCancelUrl(frontendBaseUrl + "/signup")
                        // Collect payment method at signup (default is always)  [oai_citation:2‡Stripe Docs](https://docs.stripe.com/api/checkout/sessions/object?utm_source=chatgpt.com)
                        .setPaymentMethodCollection(SessionCreateParams.PaymentMethodCollection.ALWAYS)
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPrice(priceId)
                                        .setQuantity(1L)
                                        .build()
                        )
                        .setSubscriptionData(
                                SessionCreateParams.SubscriptionData.builder()
                                        .setTrialPeriodDays(trialDays) // free trial via Checkout  [oai_citation:3‡Stripe Docs](https://docs.stripe.com/payments/checkout/free-trials?locale=en-GB&utm_source=chatgpt.com)
                                        .putMetadata("pending_signup_token", token)
                                        .build()
                        )
                        .putMetadata("pending_signup_token", token)
                        .setCustomerEmail(email) // creates / links customer based on email
                        .build();

        try {
            Session session = Session.create(params);
            return session.getUrl();
        } catch (Exception e) {
            throw new RuntimeException("Stripe checkout session creation failed", e);
        }
    }
}