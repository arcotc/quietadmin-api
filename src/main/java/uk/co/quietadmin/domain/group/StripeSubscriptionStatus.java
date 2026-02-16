package uk.co.quietadmin.domain.group;

public enum StripeSubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    UNPAID,
    INCOMPLETE,
    INCOMPLETE_EXPIRED
}
