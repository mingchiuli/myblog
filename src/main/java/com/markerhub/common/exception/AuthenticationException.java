package com.markerhub.common.exception;


/**
 * @author mingchiuli
 * @create 2022-06-12 3:10 PM
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
