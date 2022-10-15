package com.markerhub.cooperate.dto;

import com.markerhub.cooperate.CooperateEnum;

public interface MessageDto {
    CooperateEnum getMethodName();

    <T> Container<T> getData();
}
