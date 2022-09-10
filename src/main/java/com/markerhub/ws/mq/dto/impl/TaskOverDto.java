package com.markerhub.ws.mq.dto.impl;

import com.markerhub.ws.mq.dto.MessageDto;
import lombok.Data;

import java.io.Serializable;

@Data
public class TaskOverDto implements MessageDto<String>, Serializable {

    private String from;

    @Override
    public String getMethodName() {
        return "taskOver";
    }

    @Override
    public String getData() {
        return from;
    }
}
