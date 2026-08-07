package com.wwa.agenthub.platform.web.security;

import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientDescriptor;
import com.wwa.agenthub.web.security.UserContextAuthentication;

public class IntegrationClientAuthentication extends UserContextAuthentication {

    private final IntegrationClientDescriptor clientDescriptor;

    public IntegrationClientAuthentication(IntegrationClientDescriptor clientDescriptor) {
        super(clientDescriptor.user());
        this.clientDescriptor = clientDescriptor;
    }

    public IntegrationClientDescriptor clientDescriptor() {
        return clientDescriptor;
    }
}
