package com.markerhub.common.lang;

import lombok.Data;

import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2021-10-27 3:27 PM
 */
@Data
public class Result implements Serializable {

    private int code;
    private String msg;
    private Object data;

    public static Result succ(Object data) {
        return load(200, "操作成功",data); //200为正常，非200为非正常
    }

    private static Result load(int code, String msg, Object data) {
        Result r = new Result();
        r.setCode(code);
        r.setData(data);
        r.setMsg(msg);
        return r;
    }
    public static Result fail(Integer code, String msg, Object data) {
        return load(code, msg, data);
    }

    public static Result fail(String msg) {
        return load(400, msg, null);
    }

}