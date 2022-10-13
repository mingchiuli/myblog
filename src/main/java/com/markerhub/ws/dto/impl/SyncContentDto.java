package com.markerhub.ws.dto.impl;

import com.markerhub.common.vo.Content;
import com.markerhub.ws.dto.Container;
import com.markerhub.ws.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class SyncContentDto implements Serializable, MessageDto {

    Container<Content> content;

    public static String mark = "syncContent";

    @Override
    public String getMethodName() {
        return mark;
    }

    @Override
    public Container<Content> getData() {
        return content;
    }
}
