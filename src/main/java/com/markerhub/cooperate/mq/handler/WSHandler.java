package com.markerhub.cooperate.mq.handler;

import com.markerhub.cooperate.dto.MessageDto;

public interface WSHandler {
    boolean supports(MessageDto msg);
    void handle(MessageDto msg);
}
