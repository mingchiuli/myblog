package com.markerhub.common.bloom.handler;

import com.markerhub.common.bloom.BloomEnum;

public interface BloomHandler {
    BloomEnum mark();
    void doHand(Object[] args);

}
