package com.markerhub.common.bloom.handler;

public interface BloomHandler {
    String methodName();
    void handler(Object[] args);

}
