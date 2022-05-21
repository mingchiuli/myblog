package com.markerhub.common.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author mingchiuli
 * @create 2021-12-12 6:55 AM
 */
@Data
public class BlogPostDocumentVo implements Serializable {

    private Long id;
    private Long userId;
    private Integer status;
    private String title;
    private String description;
    private String content;
    private String link;
    private LocalDateTime created;
    private Float score;
    private String highlight;

}
