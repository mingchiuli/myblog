package com.markerhub.cooperate.dto.impl;

import com.markerhub.cooperate.dto.Container;
import com.markerhub.cooperate.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class SyncContentDto implements Serializable, MessageDto {

    Container<Content> content;

    @Override
    public Container<Content> getData() {
        return content;
    }

    @Data
    @AllArgsConstructor
    @Builder
    @NoArgsConstructor
    public static class Content implements Serializable {
        private String from;
        private String content;
        private String blogId;
    }
}
