package com.markerhub.ws.mq.handler.impl;

import com.markerhub.common.vo.Content;
import com.markerhub.ws.mq.dto.Container;
import com.markerhub.ws.mq.dto.MessageDto;
import com.markerhub.ws.mq.handler.WSHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class SyncContentHandler implements WSHandler {
    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }
    @Override
    public String methodName() {
        return "syncContent";
    }

    @Override
    public void doHand(MessageDto msg) {
        Container<Content> containerV3 = msg.getData();
        Content content = containerV3.getData();
        simpMessagingTemplate.convertAndSend("/topic/content/" + content.getBlogId(), content);
    }
}
