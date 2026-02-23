package uk.co.quietadmin.domain.group.dto;

import uk.co.quietadmin.domain.group.MembershipRole;

public record MemberDto(
        Long userId,
        String firstName,
        String lastName,
        String email,
        MembershipRole role
) {}