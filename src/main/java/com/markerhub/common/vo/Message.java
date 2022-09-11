package com.markerhub.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2021-12-30 12:05 PM
 */
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class Message implements Serializable {

    private String message;
    private String from;
    private String to;
    private String blogId;

}
