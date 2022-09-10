package com.markerhub.ws.mq.dto.impl;

import com.markerhub.common.vo.Message;
import com.markerhub.ws.mq.dto.MessageDto;
import lombok.Data;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import java.io.Serializable;

@Data
public class ChatDto implements Serializable, MessageDto<Message> {
    Message message;
    @Override
    public String getMethodName() {
        return "chat";
    }

    @Override
    public Message getData() {
        return message;
    }
}
