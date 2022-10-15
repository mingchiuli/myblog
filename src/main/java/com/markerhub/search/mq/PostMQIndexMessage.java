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
    private Long postId;
    public BlogIndexEnum typeEnum;
}
