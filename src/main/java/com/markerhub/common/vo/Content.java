package com.markerhub.common.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class Content implements Serializable {

    private Long from;

    private String content;

    private Long blogId;
}
