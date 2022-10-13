package com.markerhub.ws.dto.impl;

/**
 * @author mingchiuli
 * @create 2022-10-14 6:15 AM
 */

import com.markerhub.common.vo.UserEntityVo;
import com.markerhub.ws.dto.Container;
import com.markerhub.ws.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;

@Data
@AllArgsConstructor
public class PushUserDto implements Serializable, MessageDto {
    Container<Bind> data;

    public static String mark = "pushUser";

    @Override
    public String getMethodName() {
        return mark;
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

