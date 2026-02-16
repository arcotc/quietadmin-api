package uk.co.quietadmin.api.stripe;

import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.domain.group.*;
import uk.co.quietadmin.domain.signup.PendingSignup;
import uk.co.quietadmin.domain.signup.PendingSignupRepository;
import uk.co.quietadmin.domain.stripe.StripeEvent;
import uk.co.quietadmin.domain.stripe.StripeEventRepository;
import uk.co.quietadmin.domain.user.*;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.mail.EmailService;
import uk.co.quietadmin.util.SlugUtil;
import com.stripe.exception.EventDataObjectDeserializationException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeEventRepository stripeEventRepo;
    private final PendingSignupRepository pendingRepo;
    private final UserAccountRepository userRepo;
    private final QaGroupRepository groupRepo;
    private final MembershipRepository membershipRepo;
    private final EmailService emailService;

    @Transactional
    public void process(Event event, String payload) {

        log.info("Stripe event received: {} ({})", event.getType(), event.getId());

        StripeEvent persisted;
        try {
            persisted = stripeEventRepo.save(StripeEvent.builder()
                    .eventId(event.getId())
                    .type(event.getType())
                    .receivedAt(Instant.now())
                    .payload(payload)
                    .success(false)
                    .build());
        } catch (DataIntegrityViolationException dup) {
            log.info("Stripe event {} already processed (idempotent)", event.getId());
            return;
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> handleCheckoutCompleted(event);
                case "invoice.paid" -> handleInvoicePaid(event);
                case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
                default -> log.debug("Ignoring Stripe event type: {}", event.getType());
            }

            persisted.setSuccess(true);
            stripeEventRepo.save(persisted);

        } catch (Exception e) {
            log.error("Stripe webhook failed for {} ({})", event.getType(), event.getId(), e);
            throw e; // allow retry
        }
    }

    // =========================================================
    // CHECKOUT COMPLETED
    // =========================================================

    private void handleCheckoutCompleted(Event event) {

        Session session;
        try {
            session = session = deserialize(event, Session.class);
        } catch (Exception e) {
            log.error("Failed to deserialize Session for event {}", event.getId(), e);
            return;
        }

        if (session == null) {
            log.warn("Session deserialized to null for event {}", event.getId());
            return;
        }

        log.info("Processing checkout.session.completed: {}", session.getId());

        String subscriptionId = session.getSubscription();
        String customerId = session.getCustomer();

        String token = resolvePendingToken(session, subscriptionId);

        if (token == null || token.isBlank()) {
            log.warn("No pending_signup_token found for session {}", session.getId());
            return;
        }

        PendingSignup pending = pendingRepo.findByToken(token).orElse(null);
        if (pending == null) {
            log.warn("PendingSignup not found for token {}", token);
            return;
        }

        if (pending.getStatus() == PendingSignup.PendingSignupStatus.ACCOUNT_CREATED) {
            log.info("PendingSignup {} already consumed", token);
            return;
        }

        if (pending.getExpiresAt() != null && pending.getExpiresAt().isBefore(Instant.now())) {
            log.warn("PendingSignup {} expired", token);
            pending.setStatus(PendingSignup.PendingSignupStatus.EXPIRED);
            pendingRepo.save(pending);
            return;
        }

        String email = pending.getEmail();

        if (userRepo.findByEmailAndDeletedAtIsNull(email).isPresent()) {
            log.warn("User already exists for email {}, marking pending consumed", email);
            pending.setStatus(PendingSignup.PendingSignupStatus.ACCOUNT_CREATED);
            pendingRepo.save(pending);
            return;
        }

        log.info("Creating user + group for {}", email);

        // Create user
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFirstName(pending.getFirstName());
        user.setLastName("");
        user.setPasswordHash(pending.getPasswordHash());
        user.setStatus(UserStatus.INVITED);
        user.setEmailVerified(false);

        String verificationRaw = generateVerificationToken();
        String verificationHash = TokenHash.sha256(verificationRaw);

        user.setEmailVerificationToken(verificationHash);
        user.setEmailVerificationExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        user = userRepo.save(user);

        // Create group
        QaGroup group = new QaGroup();
        group.setName(pending.getGroupName());
        group.setSlug(SlugUtil.slugify(pending.getGroupName()));
        group.setCreatedBy(user.getId());
        group.setPlanType(PlanType.STANDARD);

        group.setStripeCustomerId(customerId);
        group.setStripeSubscriptionId(subscriptionId);
        group.setStripeSubscriptionStatus(StripeSubscriptionStatus.TRIALING);
        group.setStripeLastEventAt(Instant.now());

        group = groupRepo.save(group);

        // Membership
        Membership m = new Membership();
        m.setUserId(user.getId());
        m.setGroupId(group.getId());
        m.setRole("ADMIN");
        membershipRepo.save(m);

        snapshotSubscription(subscriptionId, group);

        pending.setStatus(PendingSignup.PendingSignupStatus.ACCOUNT_CREATED);
        pending.setStripeCustomerId(customerId);
        pending.setStripeSubscriptionId(subscriptionId);
        pendingRepo.save(pending);

        sendVerificationEmail(user, group.getName(), verificationRaw);

        log.info("Account successfully created for {}", email);
    }

    // =========================================================
    // INVOICE PAID
    // =========================================================

    private void handleInvoicePaid(Event event) {

        Invoice invoice = deserialize(event, Invoice.class);
        if (invoice == null) return;

        String subscriptionId = invoice.getSubscription();
        if (subscriptionId == null) return;

        QaGroup group = groupRepo.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (group == null) {
            log.warn("No group found for subscription {}", subscriptionId);
            return;
        }

        group.setStripeSubscriptionStatus(StripeSubscriptionStatus.ACTIVE);
        group.setStripeLastEventAt(Instant.now());

        snapshotSubscription(subscriptionId, group);

        groupRepo.save(group);

        log.info("Subscription marked ACTIVE for group {}", group.getId());
    }

    private void handleInvoicePaymentFailed(Event event) {

        Invoice invoice = deserialize(event, Invoice.class);
        if (invoice == null) return;

        String subscriptionId = invoice.getSubscription();
        if (subscriptionId == null) return;

        QaGroup group = groupRepo.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (group == null) return;

        group.setStripeSubscriptionStatus(StripeSubscriptionStatus.PAST_DUE);
        group.setStripeLastEventAt(Instant.now());

        groupRepo.save(group);

        log.warn("Subscription marked PAST_DUE for group {}", group.getId());
    }

    private void handleSubscriptionDeleted(Event event) {

        Subscription sub = deserialize(event, Subscription.class);
        if (sub == null) return;

        String subscriptionId = sub.getId();
        if (subscriptionId == null) return;

        QaGroup group = groupRepo.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (group == null) return;

        group.setStripeSubscriptionStatus(StripeSubscriptionStatus.CANCELED);
        group.setStripeCanceledAt(Instant.now());
        group.setStripeLastEventAt(Instant.now());

        groupRepo.save(group);

        log.warn("Subscription canceled for group {}", group.getId());
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String resolvePendingToken(Session session, String subscriptionId) {

        if (session.getMetadata() != null) {
            String token = session.getMetadata().get("pending_signup_token");
            if (token != null) return token;
        }

        if (subscriptionId != null) {
            try {
                Subscription sub = Subscription.retrieve(subscriptionId);
                if (sub.getMetadata() != null) {
                    return sub.getMetadata().get("pending_signup_token");
                }
            } catch (Exception e) {
                log.warn("Could not retrieve subscription metadata {}", subscriptionId, e);
            }
        }

        return null;
    }

    private void snapshotSubscription(String subscriptionId, QaGroup group) {

        if (subscriptionId == null) return;

        try {
            Subscription sub = Subscription.retrieve(subscriptionId);

            try {
                StripeSubscriptionStatus mapped =
                        StripeSubscriptionStatus.valueOf(sub.getStatus().toUpperCase());
                group.setStripeSubscriptionStatus(mapped);
            } catch (IllegalArgumentException ex) {
                log.error("Unknown Stripe subscription status {}", sub.getStatus());
                group.setStripeSubscriptionStatus(StripeSubscriptionStatus.INCOMPLETE);
            }

            if (sub.getTrialEnd() != null)
                group.setStripeTrialEnd(Instant.ofEpochSecond(sub.getTrialEnd()));

            if (sub.getCurrentPeriodEnd() != null)
                group.setStripeCurrentPeriodEnd(Instant.ofEpochSecond(sub.getCurrentPeriodEnd()));

            if (sub.getCancelAt() != null)
                group.setStripeCancelAt(Instant.ofEpochSecond(sub.getCancelAt()));

            if (sub.getCanceledAt() != null)
                group.setStripeCanceledAt(Instant.ofEpochSecond(sub.getCanceledAt()));

            group.setStripeLastSyncAt(Instant.now());

        } catch (Exception e) {
            log.warn("Subscription snapshot failed {}", subscriptionId, e);
        }
    }

    private void sendVerificationEmail(UserAccount user, String groupName, String rawToken) {

        String formattedExpiry =
                DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' HH:mm")
                        .withZone(ZoneId.of("Europe/London"))
                        .format(user.getEmailVerificationExpiresAt());

        try {
            emailService.sendVerificationEmail(
                    user.getEmail(),
                    rawToken,
                    formattedExpiry,
                    user.getFirstName(),
                    groupName
            );
            log.info("Verification email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Verification email failed for {}. Account remains created.",
                    user.getEmail(), e);
        }
    }

    private String generateVerificationToken() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private <T> T deserialize(Event event, Class<T> type) {

        try {
            Object obj = event.getDataObjectDeserializer().deserializeUnsafe();

            if (obj == null) {
                log.warn("Stripe event {} deserialized to null", event.getId());
                return null;
            }

            if (!type.isInstance(obj)) {
                log.warn("Stripe event {} is not of expected type {} but was {}",
                        event.getId(),
                        type.getSimpleName(),
                        obj.getClass().getSimpleName());
                return null;
            }

            return type.cast(obj);

        } catch (EventDataObjectDeserializationException e) {
            log.error("Stripe deserialization failed for event {}",
                    event.getId(), e);
            return null;
        }
    }
}