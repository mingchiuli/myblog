package com.markerhub.ws.mq.dto.impl;

import com.markerhub.ws.mq.dto.Container;
import com.markerhub.ws.mq.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class TaskOverDto implements MessageDto, Serializable {

    private Container<String> from;

    @Override
    public String getMethodName() {
        return "taskOver";
    }

    @Override
    public Container<String> getData() {
        return from;
    }
}
