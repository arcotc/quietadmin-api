package uk.co.quietadmin.sharedlinks.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "shared_link",
        indexes = {
                @Index(name = "idx_sl_group_archived", columnList = "group_id,archived_at"),
                @Index(name = "idx_sl_group_folder_sort", columnList = "group_id,folder_id,sort_order"),
                @Index(name = "idx_sl_group_pinned", columnList = "group_id,is_pinned")
        })
public class SharedLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private SharedLinkFolder folder;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isArchived() {
        return archivedAt != null;
    }
}