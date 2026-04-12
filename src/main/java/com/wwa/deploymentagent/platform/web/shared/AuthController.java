package com.wwa.deploymentagent.platform.web.shared;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.AuthResponseDto;
import com.wwa.deploymentagent.contracts.dto.LoginRequestDto;
import com.wwa.deploymentagent.contracts.enums.Role;
import com.wwa.deploymentagent.domain.auth.AuthService;
import com.wwa.deploymentagent.domain.auth.PermissionResolver;
import com.wwa.deploymentagent.web.security.UserContextAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * AuthController – login/logout/session check endpoints.
 */
@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String USER_CONTEXT_ATTR = "USER_CONTEXT";
    private static final String GUEST_USER_ID = "guest";
    private static final String GUEST_DISPLAY_NAME = "Guest Viewer";

    private final AuthService authService;
    private final PermissionResolver permissionResolver;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(
            @RequestBody LoginRequestDto body,
            HttpServletRequest request) {

        UserContext userContext = authService.authenticate(body.employeeId(), body.password());

        // Create/update session
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_CONTEXT_ATTR, userContext);

        // Set security context for the current request
        UserContextAuthentication auth = new UserContextAuthentication(userContext);
        SecurityContextHolder.getContext().setAuthentication(auth);

        return ResponseEntity.ok(new AuthResponseDto(
                userContext.userId(),
                userContext.role(),
                userContext.roles(),
                userContext.permissions(),
                userContext.displayName(),
                userContext.scopes()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDto> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        UserContext userContext = (UserContext) session.getAttribute(USER_CONTEXT_ATTR);
        if (userContext == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(new AuthResponseDto(
                userContext.userId(),
                userContext.role(),
                userContext.roles(),
                userContext.permissions(),
                userContext.displayName(),
                userContext.scopes()
        ));
    }

    /**
     * Anonymous guest login. Creates a session with a synthetic GUEST
     * UserContext so visitors can browse the platform read-only without
     * any registered Team Book account. All write operations are blocked
     * by GuestReadOnlyFilter regardless of UI state.
     */
    @PostMapping("/guest")
    public ResponseEntity<AuthResponseDto> loginAsGuest(HttpServletRequest request) {
        List<String> roles = List.of(Role.GUEST.name());
        Set<String> permissions = permissionResolver.resolvePermissions(roles);

        UserContext guest = new UserContext(
                GUEST_USER_ID,
                Role.GUEST.name(),
                roles,
                permissions,
                GUEST_DISPLAY_NAME,
                List.of()
        );

        HttpSession session = request.getSession(true);
        session.setAttribute(USER_CONTEXT_ATTR, guest);

        UserContextAuthentication auth = new UserContextAuthentication(guest);
        SecurityContextHolder.getContext().setAuthentication(auth);

        return ResponseEntity.ok(new AuthResponseDto(
                guest.userId(),
                guest.role(),
                guest.roles(),
                guest.permissions(),
                guest.displayName(),
                guest.scopes()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }
}
