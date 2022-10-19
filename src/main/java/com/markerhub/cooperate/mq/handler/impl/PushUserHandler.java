package com.markerhub.cooperate.mq.handler.impl;

import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
import com.markerhub.cooperate.dto.impl.PushUserDto;
import com.markerhub.cooperate.mq.handler.WSHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.ArrayList;

@Component
public class PushUserHandler implements WSHandler {
    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public void setSimpMessagingTemplate(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Override
    public boolean supports(MessageDto msg) {
        return msg instanceof PushUserDto;
    }

    @Override
    public void handler(MessageDto msg) {
        Container<PushUserDto.Bind> containerV1 = msg.getData();
        PushUserDto.Bind dataV1 = containerV1.getData();
        String blogIdV1 = dataV1.getBlogId();
        ArrayList<UserEntityVo> usersV1 = dataV1.getUsers();
        simpMessagingTemplate.convertAndSendToUser(blogIdV1,"/topic/users", usersV1);
    }
}
