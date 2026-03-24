package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.AuthResponseDto;
import com.wwa.deploymentagent.contracts.dto.LoginRequestDto;
import com.wwa.deploymentagent.domain.auth.AuthService;
import com.wwa.deploymentagent.web.security.UserContextAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController – login/logout/session check endpoints.
 */
@RestController
@RequestMapping("/api/deployment-agent/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String USER_CONTEXT_ATTR = "USER_CONTEXT";

    private final AuthService authService;

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
