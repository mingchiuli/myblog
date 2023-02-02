package com.markerhub.log.controller;

import com.markerhub.common.lang.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;


/**
 * @author mingchiuli
 * @create 2022-01-04 4:35 PM
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class LogController {

    private final RabbitListenerEndpointRegistry registry;

    @MessageMapping("/startMQ")
    @PreAuthorize("hasRole('admin')")
    public Result start() {
        MessageListenerContainer logContainer = registry.getListenerContainer("log");

        if (!logContainer.isRunning()) {
            logContainer.start();
        }

        return Result.succ(null);
    }

    @MessageMapping("/stopMQ")
    @PreAuthorize("hasRole('admin')")
    public Result stop() {
        MessageListenerContainer logContainer = registry.getListenerContainer("log");

        if (logContainer.isRunning()) {
            logContainer.stop();
        }

        return Result.succ(null);
    }

}
