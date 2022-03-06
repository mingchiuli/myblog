package com.markerhub.search.mq;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2021-12-13 10:46 AM
 */
@Data
@AllArgsConstructor
public class PostMQIndexMessage implements Serializable {

    public static final String UPDATE = "update";
    public static final String REMOVE = "remove";
    public static final String CREATE = "create";


    private Long postId;

    private String type;

}
