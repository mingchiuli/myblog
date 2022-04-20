package com.markerhub.common.vo;

import java.security.Principal;

/**
 * @author mingchiuli
 * @create 2022-02-23 2:07 PM
 */
public class StompPrincipal implements Principal {
    String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
