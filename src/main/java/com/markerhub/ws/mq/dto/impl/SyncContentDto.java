package com.markerhub.ws.mq.dto.impl;

import com.markerhub.common.vo.Content;
import com.markerhub.ws.mq.dto.Container;
import com.markerhub.ws.mq.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class SyncContentDto implements Serializable, MessageDto {

    Container<Content> content;

    @Override
    public String getMethodName() {
        return "syncContent";
    }

    @Override
    public Container<Content> getData() {
        return content;
    }
}
