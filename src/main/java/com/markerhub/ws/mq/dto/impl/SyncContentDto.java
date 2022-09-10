package com.markerhub.ws.mq.dto.impl;

import com.markerhub.common.vo.Content;
import com.markerhub.ws.mq.dto.MessageDto;
import lombok.Data;

import java.io.Serializable;

@Data
public class SyncContentDto implements Serializable, MessageDto<Content> {

    Content content;

    @Override
    public String getMethodName() {
        return "syncContent";
    }

    @Override
    public Content getData() {
        return content;
    }
}
