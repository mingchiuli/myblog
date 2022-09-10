package com.markerhub.ws.mq.dto;

public interface MessageDto<T> {
    String getMethodName();

    T getData();
}
