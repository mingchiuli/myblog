package com.markerhub.cooperate.dto.impl;

import com.markerhub.common.vo.Message;
import com.markerhub.cooperate.CooperateEnum;
import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ChatDto implements Serializable, MessageDto {
    Container<Message> message;

    @Override
    public CooperateEnum getMethodName() {
        return CooperateEnum.CHAT;
    }

    @Override
    public Container<Message> getData() {
        return message;
    }
}
