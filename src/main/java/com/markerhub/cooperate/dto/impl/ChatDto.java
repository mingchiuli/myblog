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
public class ChatDto implements Serializable, MessageDto {
    Container<Message> message;

    @Override
    public Container<Message> getData() {
        return message;
    }

    @Data
    @AllArgsConstructor
    @Builder
    @NoArgsConstructor
    public static class Message implements Serializable {
        private String message;
        private String from;
        private String to;
        private String blogId;
    }
}
