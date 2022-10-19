package com.markerhub.cooperate.dto.impl;

import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;

@Data
@AllArgsConstructor
public class DestroyDto implements Serializable, MessageDto {
    Container<Bind> data;

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
