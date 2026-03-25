package uk.co.quietadmin.domain.team.dto;

public record TeamDto(
        Long id,
        String name,
        String description,
        long membersCount,
        boolean isMember
) {}