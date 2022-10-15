package com.markerhub.cooperate.dto.impl;

import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.cooperate.CooperateEnum;
import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;

@Data
@AllArgsConstructor
public class InitDto implements Serializable, MessageDto {
    Container<Bind> data;

    @Override
    public CooperateEnum getMethodName() {
        return CooperateEnum.INIT;
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
