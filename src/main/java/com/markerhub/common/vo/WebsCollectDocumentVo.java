package com.markerhub.common.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author mingchiuli
 * @create 2022-01-29 2:57 PM
 */
@Data
public class WebsCollectDocumentVo implements Serializable {
    private String id;
    private Integer status;
    private String title;
    private String description;
    private String link;
    private LocalDateTime created;
    private Float score;
    private String highlight;


}
