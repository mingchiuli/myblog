package com.markerhub.cooperate.mq.handler.impl;

import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
import com.markerhub.cooperate.dto.impl.SyncContentDto;
import com.markerhub.cooperate.mq.handler.WSHandler;
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
    public boolean supports(MessageDto msg) {
        return msg instanceof SyncContentDto;
    }

    @Override
    public void handler(MessageDto msg) {
        Container<SyncContentDto.Content> containerV3 = msg.getData();
        SyncContentDto.Content content = containerV3.getData();
        simpMessagingTemplate.convertAndSend("/topic/content/" + content.getBlogId(), content);
    }
}
