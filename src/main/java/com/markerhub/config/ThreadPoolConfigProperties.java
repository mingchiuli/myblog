package com.markerhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


//跟配置文件绑定
@ConfigurationProperties(prefix = "vueblog.thread")
@Data
public class ThreadPoolConfigProperties {

    private Integer coreSize;
    private Integer maxSize;
    private Integer keepAliveTime;
}
