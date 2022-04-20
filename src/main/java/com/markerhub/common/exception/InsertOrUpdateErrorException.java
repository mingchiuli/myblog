package com.markerhub.common.exception;

/**
 * @author mingchiuli
 * @create 2022-04-20 9:28 AM
 */
public class InsertOrUpdateErrorException extends RuntimeException {
    public InsertOrUpdateErrorException(String msg) {
        super(msg);
    }
}
