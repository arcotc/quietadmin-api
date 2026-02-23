package uk.co.quietadmin.domain.group.dto;

public record MemberDto(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String role
) {}