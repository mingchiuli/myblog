package com.markerhub.ws.dto.impl;

import com.markerhub.ws.dto.Container;
import com.markerhub.ws.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class TaskOverDto implements MessageDto, Serializable {

    private Container<String> from;

    public static String mark = "taskOver";

    @Override
    public String getMethodName() {
        return mark;
    }

    @Override
    public Container<String> getData() {
        return from;
    }
}
