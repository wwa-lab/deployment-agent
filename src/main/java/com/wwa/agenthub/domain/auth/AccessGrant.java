package com.wwa.agenthub.domain.auth;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.util.AccessScopeListJsonAttributeConverter;
import com.wwa.agenthub.util.StringListJsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "DA_ACCESS_GRANT")
@Getter
@Setter
public class AccessGrant {

    @Id
    @Column(name = "employee_id", length = 255, nullable = false, updatable = false)
    private String employeeId;

    @Column(name = "display_name_snapshot", length = 255, nullable = false)
    private String displayNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_status", length = 30, nullable = false)
    private AccessGrantStatus grantStatus = AccessGrantStatus.ACTIVE;

    @Column(name = "assigned_roles", columnDefinition = "CLOB", nullable = false)
    @Convert(converter = StringListJsonAttributeConverter.class)
    private List<String> assignedRoles = new ArrayList<>();

    @Column(name = "scope_grants", columnDefinition = "CLOB", nullable = false)
    @Convert(converter = AccessScopeListJsonAttributeConverter.class)
    private List<AccessScope> scopeGrants = new ArrayList<>();

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_by", length = 255, nullable = false, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 255, nullable = false)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
