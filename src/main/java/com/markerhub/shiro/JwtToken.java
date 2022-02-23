package com.markerhub.shiro;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * @author mingchiuli
 * @create 2021-10-27 8:04 PM
 */
public class JwtToken implements AuthenticationToken {

    private final String token;

    public JwtToken(String token) {
        this.token = token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
