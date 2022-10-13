package com.markerhub.ws.dto.impl;

import com.markerhub.common.vo.Message;
import com.markerhub.ws.dto.Container;
import com.markerhub.ws.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ChatDto implements Serializable, MessageDto {
    Container<Message> message;

    public static String mark = "chat";

    @Override
    public String getMethodName() {
        return mark;
    }

    @Override
    public Container<Message> getData() {
        return message;
    }
}
