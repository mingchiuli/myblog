package com.markerhub.ws.mq.handler.impl;

import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.ws.dto.Container;
import com.markerhub.ws.dto.MessageDto;
import com.markerhub.ws.dto.impl.InitDto;
import com.markerhub.ws.dto.impl.PushUserDto;
import com.markerhub.ws.mq.handler.WSHandler;
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
    public String methodName() {
        return PushUserDto.mark;
    }

    @Override
    public void doHand(MessageDto msg) {
        Container<InitDto.Bind> containerV1 = msg.getData();
        InitDto.Bind dataV1 = containerV1.getData();
        String blogIdV1 = dataV1.getBlogId();
        ArrayList<UserEntityVo> usersV1 = dataV1.getUsers();
        simpMessagingTemplate.convertAndSendToUser(blogIdV1,"/topic/users", usersV1);
    }
}
