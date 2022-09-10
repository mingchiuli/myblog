package com.markerhub.log.controller;

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

        return Result.succ(null);
    }

    @GetMapping("/stopMQ")
    @PreAuthorize("hasRole('admin')")
    public Result stop() {
        MessageListenerContainer logContainer = registry.getListenerContainer("log");

        if (logContainer.isRunning()) {
            logContainer.stop();
        }

        return Result.succ(null);
    }

}
