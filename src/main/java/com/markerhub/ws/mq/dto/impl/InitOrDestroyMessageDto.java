package com.markerhub.ws.mq.dto.impl;

import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.ws.mq.dto.MessageDto;
import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;

@Data
public class InitOrDestroyMessageDto implements Serializable, MessageDto<InitOrDestroyMessageDto.Data> {
    Data data;

    @Override
    public String getMethodName() {
        return "init";
    }


    @lombok.Data
    public static class Data implements Serializable {
        String blogId;
        ArrayList<UserEntityVo> users;
    }
}
