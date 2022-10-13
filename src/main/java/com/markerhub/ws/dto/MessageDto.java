package com.markerhub.ws.dto;

public interface MessageDto {
    String getMethodName();

    <T> Container<T> getData();
}
