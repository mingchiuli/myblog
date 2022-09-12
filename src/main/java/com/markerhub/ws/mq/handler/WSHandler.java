package com.markerhub.ws.mq.handler;

import com.markerhub.ws.mq.dto.MessageDto;

public interface WSHandler {
    String methodName();
    void handler(MessageDto msg);
}
