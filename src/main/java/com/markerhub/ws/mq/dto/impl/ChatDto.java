package com.markerhub.ws.mq.dto.impl;

import com.markerhub.common.vo.Message;
import com.markerhub.ws.mq.dto.Container;
import com.markerhub.ws.mq.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ChatDto implements Serializable, MessageDto {
    Container<Message> message;
    @Override
    public String getMethodName() {
        return "chat";
    }

    @Override
    public Container<Message> getData() {
        return message;
    }
}
