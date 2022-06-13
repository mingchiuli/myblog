package com.markerhub.controller;

import com.markerhub.common.lang.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @author mingchiuli
 * @create 2022-01-04 4:35 PM
 */
@RestController
@Slf4j
public class LogController {

    RabbitListenerEndpointRegistry registry;

    @Autowired
    public void setRegistry(RabbitListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/startMQ")
    @PreAuthorize("hasRole('admin')")
    public Result start() {
        MessageListenerContainer logContainer = registry.getListenerContainer("log");

        if (!logContainer.isRunning()) {
            logContainer.start();
        }

        log.info("消息队列日志监听器已开启");

        return Result.succ(null);
    }

    @GetMapping("/stopMQ")
    @PreAuthorize("hasRole('admin')")
    public Result stop() {
        MessageListenerContainer logContainer = registry.getListenerContainer("log");

        if (logContainer.isRunning()) {
            logContainer.stop();
        }

        log.info("消息队列日志监听器已关闭");

        return Result.succ(null);
    }

}
