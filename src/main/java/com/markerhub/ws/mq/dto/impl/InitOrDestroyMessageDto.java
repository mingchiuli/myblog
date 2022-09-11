package com.markerhub.ws.mq.dto.impl;

import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.ws.mq.dto.Container;
import com.markerhub.ws.mq.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;

@Data
@AllArgsConstructor
public class InitOrDestroyMessageDto implements Serializable, MessageDto {
    Container<Bind> data;

    @Override
    public String getMethodName() {
        return "init";
    }

    @Override
    public Container<Bind> getData() {
        return data;
    }


    @Data
    @AllArgsConstructor
    public static class Bind implements Serializable {
        String blogId;
        ArrayList<UserEntityVo> users;
    }
}
