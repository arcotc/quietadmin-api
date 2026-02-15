package uk.co.quietadmin.api.stripe;

import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.Subscription;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.group.*;
import uk.co.quietadmin.domain.signup.PendingSignup;
import uk.co.quietadmin.domain.signup.PendingSignupRepository;
import uk.co.quietadmin.domain.stripe.StripeEvent;
import uk.co.quietadmin.domain.stripe.StripeEventRepository;
import uk.co.quietadmin.domain.user.*;
import uk.co.quietadmin.util.SlugUtil;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeEventRepository stripeEventRepo;
    private final PendingSignupRepository pendingRepo;

    private final UserAccountRepository userRepo;
    private final QaGroupRepository groupRepo;
    private final MembershipRepository membershipRepo;

    @Transactional
    public void process(Event event, String payload) {

        // Idempotency guard: Stripe retries webhooks
        if (stripeEventRepo.existsByEventId(event.getId())) {
            return;
        }
        stripeEventRepo.save(StripeEvent.builder()
                .eventId(event.getId())
                .type(event.getType())
                .receivedAt(Instant.now())
                .payload(payload)
                .success(false)
                .build());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            // Optional later:
            // case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            // case "invoice.paid" -> handleInvoicePaid(event);
            default -> {
                // ignore
            }
        }
    }

    private void handleCheckoutCompleted(Event event) {

        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof Session session)) return;

        // Ensure this is subscription Checkout
        String subscriptionId = session.getSubscription();
        String customerId = session.getCustomer();

        // Get signup token from metadata
        String token = null;
        if (session.getMetadata() != null) {
            token = session.getMetadata().get("pending_signup_token");
        }
        if (token == null || token.isBlank()) return;

        PendingSignup pending = pendingRepo.findByToken(token).orElse(null);
        if (pending == null) return;

        // Double-check email isn't already registered (race protection)
        String email = pending.getEmail();
        if (userRepo.existsByEmail(email)) {
            pendingRepo.delete(pending);
            return;
        }

        // Create user
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFirstName(pending.getFirstName());
        user.setPasswordHash(pending.getPasswordHash());
        user.setStatus(UserStatus.ACTIVE); // adapt to your enum
        user.setEmailVerified(false);
        user = userRepo.save(user);

        // Create group
        QaGroup group = new QaGroup();
        group.setName(pending.getGroupName());
        group.setSlug(SlugUtil.slugify(pending.getGroupName()));
        group.setCreatedBy(user.getId());
        group.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        group.setPlanType(PlanType.STANDARD);
        group.setStripeCustomerId(customerId);
        // trial end can be set from Stripe subscription if you fetch it; see below
        group = groupRepo.save(group);

        // Create membership (ADMIN)
        Membership m = new Membership();
        m.setUserId(user.getId());
        m.setGroupId(group.getId());
        m.setRole("ADMIN");
        membershipRepo.save(m);

        // Optional: fetch Subscription to set trial_ends_at precisely
        try {
            if (subscriptionId != null) {
                Subscription sub = Subscription.retrieve(subscriptionId);
                if (sub.getTrialEnd() != null) {
                    group.setTrialEndsAt(Instant.ofEpochSecond(sub.getTrialEnd()));
                    groupRepo.save(group);
                }
            }
        } catch (Exception ignored) {}

        // Consume pending record
        pendingRepo.delete(pending);
    }
}