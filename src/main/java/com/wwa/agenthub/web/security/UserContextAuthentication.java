package com.wwa.agenthub.web.security;

import com.wwa.agenthub.contracts.UserContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.stream.Stream;

/**
 * Spring Security Authentication token backed by a {@link UserContext}.
 * Roles and permissions are mapped to Spring authorities using "ROLE_" and "PERM_" prefixes.
 */
public class UserContextAuthentication extends AbstractAuthenticationToken {

    private final UserContext userContext;

    public UserContextAuthentication(UserContext userContext) {
        super(Stream.concat(
                userContext.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                userContext.permissions().stream()
                        .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
        ).toList());
        this.userContext = userContext;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public UserContext getPrincipal() {
        return userContext;
    }
}
