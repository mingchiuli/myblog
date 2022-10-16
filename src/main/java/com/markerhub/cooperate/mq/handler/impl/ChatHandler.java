package com.markerhub.cooperate.mq.handler.impl;

import com.markerhub.cooperate.CooperateEnum;
import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
import com.markerhub.cooperate.dto.impl.ChatDto;
import com.markerhub.cooperate.mq.handler.WSHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatHandler implements WSHandler {

    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Override
    public CooperateEnum methodName() {
        return CooperateEnum.CHAT;
    }

    @Override
    public void doHand(MessageDto msg) {
        Container<ChatDto.Message> containerV4 = msg.getData();
        ChatDto.Message message = containerV4.getData();
        String id = message.getBlogId();
        String to = message.getTo();
        simpMessagingTemplate.convertAndSendToUser(to, "/" + id + "/queue/chat", message);
    }
}
