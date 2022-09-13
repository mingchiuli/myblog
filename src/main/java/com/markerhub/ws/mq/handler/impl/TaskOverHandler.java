package com.markerhub.ws.mq.handler.impl;

import com.markerhub.ws.mq.dto.Container;
import com.markerhub.ws.mq.dto.MessageDto;
import com.markerhub.ws.mq.handler.WSHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskOverHandler implements WSHandler {

    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }
    @Override
    public String methodName() {
        return "taskOver";
    }

    @Override
    public void handler(MessageDto msg) {
        Container<String> containerV2 = msg.getData();
        String from = containerV2.getData();
        simpMessagingTemplate.convertAndSend("/topic/over", from);
    }
}