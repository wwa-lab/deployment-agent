package com.wwa.deploymentagent.web.security;

import com.wwa.deploymentagent.contracts.UserContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Spring Security Authentication token backed by a {@link UserContext}.
 * The role is mapped to a Spring authority using the "ROLE_" prefix convention.
 */
public class UserContextAuthentication extends AbstractAuthenticationToken {

    private final UserContext userContext;

    public UserContextAuthentication(UserContext userContext) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + userContext.role())));
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
