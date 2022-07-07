package com.markerhub.common.exception;

/**
 * @author mingchiuli
 * @create 2022-07-07 11:06 AM
 */
public class NoFoundException extends RuntimeException {
    public NoFoundException(String message) {
        super(message);
    }
}
