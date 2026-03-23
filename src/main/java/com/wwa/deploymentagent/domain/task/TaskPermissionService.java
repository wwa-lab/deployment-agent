package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.domain.auth.AuthService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Centralized task-level authorization rules.
 *
 * <p>Mutation permissions are granted to:
 * <ul>
 *   <li>the task owner</li>
 *   <li>DEVOPS_ADMIN users</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TaskPermissionService {

    private final AuthService authService;

    public void assertOwnerOrAdmin(Task task, UserContext user, String action) {
        if (!isOwnerOrAdmin(task, user)) {
            throw new ForbiddenAppException(action);
        }
    }

    public boolean isOwnerOrAdmin(Task task, UserContext user) {
        if (task == null || user == null) {
            return false;
        }
        if ("DEVOPS_ADMIN".equals(user.role())) {
            return true;
        }
        return isOwner(task, user);
    }

    private boolean isOwner(Task task, UserContext user) {
        String normalizedOwner = normalize(task.getOwner());
        if (normalizedOwner == null) {
            return false;
        }

        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, user.userId());

        String displayName = authService.getDisplayName(user.userId());
        String cleanDisplayName = stripRoleSuffix(displayName);
        addCandidate(candidates, cleanDisplayName);
        addCandidate(candidates, firstToken(cleanDisplayName));

        return candidates.contains(normalizedOwner);
    }

    private void addCandidate(Set<String> candidates, String raw) {
        String normalized = normalize(raw);
        if (normalized != null) {
            candidates.add(normalized);
        }
    }

    private String stripRoleSuffix(String displayName) {
        if (displayName == null) {
            return "";
        }
        return displayName.replaceFirst("\\s*\\(.*\\)$", "").trim();
    }

    private String firstToken(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        return displayName.split("\\s+")[0];
    }

    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.toLowerCase().replaceAll("[^a-z0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }
}
