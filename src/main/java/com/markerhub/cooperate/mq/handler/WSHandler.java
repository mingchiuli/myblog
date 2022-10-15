package com.markerhub.cooperate.mq.handler;

import com.markerhub.cooperate.CooperateEnum;
import com.markerhub.cooperate.dto.MessageDto;

public interface WSHandler {
    CooperateEnum methodName();
    void doHand(MessageDto msg);
}
