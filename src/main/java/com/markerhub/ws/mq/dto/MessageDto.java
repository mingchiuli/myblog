package com.markerhub.ws.mq.dto;

public interface MessageDto {
    String getMethodName();

    <T> Container<T> getData();
}
