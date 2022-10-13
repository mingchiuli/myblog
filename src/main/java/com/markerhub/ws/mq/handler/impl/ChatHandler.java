package com.markerhub.ws.mq.handler.impl;

import com.markerhub.common.vo.Message;
import com.markerhub.ws.dto.Container;
import com.markerhub.ws.dto.MessageDto;
import com.markerhub.ws.dto.impl.ChatDto;
import com.markerhub.ws.mq.handler.WSHandler;
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
    public String methodName() {
        return ChatDto.mark;
    }

    @Override
    public void doHand(MessageDto msg) {
        Container<Message> containerV4 = msg.getData();
        Message message = containerV4.getData();
        String id = message.getBlogId();
        String to = message.getTo();
        simpMessagingTemplate.convertAndSendToUser(to, "/" + id + "/queue/chat", message);
    }
}
