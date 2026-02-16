package uk.co.quietadmin.domain.group.dto;

public record MemberDto(
        Long userId,
        String name,
        String email,
        String role
) {}