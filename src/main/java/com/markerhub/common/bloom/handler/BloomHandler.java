package com.markerhub.common.bloom.handler;

public interface BloomHandler {
    boolean supports(Class<?> handler);
    void handle(Object[] args);
}
