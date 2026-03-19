package uk.co.quietadmin.domain.team;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(
        name = "notice_team_visibility",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notice_team",
                columnNames = {"notice_id", "team_id"}
        )
)
public class NoticeTeamVisibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;
}