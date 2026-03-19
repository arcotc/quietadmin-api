package uk.co.quietadmin.domain.group.dto;

import uk.co.quietadmin.domain.group.MembershipRole;
import uk.co.quietadmin.domain.user.UserStatus;

public record MemberDto(
        Long userId,
        String firstName,
        String lastName,
        String email,
        MembershipRole role,
        UserStatus status
) {}