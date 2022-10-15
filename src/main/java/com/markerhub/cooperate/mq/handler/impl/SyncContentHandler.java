package com.markerhub.cooperate.mq.handler.impl;

import com.markerhub.common.vo.Content;
import com.markerhub.cooperate.CooperateEnum;
import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
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
    public CooperateEnum methodName() {
        return CooperateEnum.SYNC_CONTENT;
    }

    @Override
    public void doHand(MessageDto msg) {
        Container<Content> containerV3 = msg.getData();
        Content content = containerV3.getData();
        simpMessagingTemplate.convertAndSend("/topic/content/" + content.getBlogId(), content);
    }
}
