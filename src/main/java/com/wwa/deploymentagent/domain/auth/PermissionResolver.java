package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.enums.PermissionKey;
import com.wwa.deploymentagent.contracts.enums.Role;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class PermissionResolver {

    private static final Map<Role, Set<PermissionKey>> ROLE_PERMISSIONS = buildRolePermissions();

    public Set<String> resolvePermissions(Collection<String> roles) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (roles == null) {
            return Set.of();
        }

        for (String roleName : roles) {
            if (roleName == null || roleName.isBlank()) {
                continue;
            }
            Role role = Role.valueOf(roleName.trim());
            for (PermissionKey permission : ROLE_PERMISSIONS.getOrDefault(role, Set.of())) {
                permissions.add(permission.value());
            }
        }

        return Set.copyOf(permissions);
    }

    private static Map<Role, Set<PermissionKey>> buildRolePermissions() {
        EnumMap<Role, Set<PermissionKey>> permissions = new EnumMap<>(Role.class);

        permissions.put(Role.DEVELOPER, Set.of(
                PermissionKey.PLATFORM_ENTER,
                PermissionKey.RELEASE_VIEW,
                PermissionKey.RELEASE_UPLOAD,
                PermissionKey.RELEASE_RUNDOWN_EDIT,
                PermissionKey.RELEASE_RUNDOWN_ARCHIVE,
                PermissionKey.RELEASE_RUNDOWN_START,
                PermissionKey.RELEASE_RUNDOWN_FAIL
        ));

        permissions.put(Role.TL, union(
                permissions.get(Role.DEVELOPER),
                Set.of(
                        PermissionKey.TASK_EDIT,
                        PermissionKey.TASK_RUN,
                        PermissionKey.TASK_REVIEW
                )
        ));

        permissions.put(Role.DEVOPS_ADMIN, union(
                permissions.get(Role.TL),
                Set.of(
                        PermissionKey.CONFIG_MANAGE,
                        PermissionKey.AUDIT_VIEW,
                        PermissionKey.PLATFORM_AUDIT_VIEW,
                        PermissionKey.ACCESS_MANAGE,
                        PermissionKey.PLATFORM_ACCESS_MANAGE,
                        PermissionKey.RELEASE_VIEW_ARCHIVED,
                        PermissionKey.RELEASE_RUNDOWN_RESTORE,
                        PermissionKey.RELEASE_RUNDOWN_PURGE
                )
        ));

        permissions.put(Role.AUDIT, Set.of(
                PermissionKey.PLATFORM_ENTER,
                PermissionKey.AUDIT_VIEW,
                PermissionKey.PLATFORM_AUDIT_VIEW
        ));
        permissions.put(Role.MANAGEMENT, Set.of(
                PermissionKey.PLATFORM_ENTER,
                PermissionKey.AUDIT_VIEW,
                PermissionKey.PLATFORM_AUDIT_VIEW
        ));

        return Map.copyOf(permissions);
    }

    private static Set<PermissionKey> union(Set<PermissionKey> left, Set<PermissionKey> right) {
        LinkedHashSet<PermissionKey> merged = new LinkedHashSet<>(left);
        merged.addAll(right);
        return Set.copyOf(merged);
    }
}
