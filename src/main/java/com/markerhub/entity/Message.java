package com.markerhub.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2021-12-30 12:05 PM
 */
@Data
public class Message implements Serializable {

    private String message;

    private String from;

    private Long to;

}
