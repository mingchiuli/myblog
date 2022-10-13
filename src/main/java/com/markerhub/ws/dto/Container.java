package com.markerhub.ws.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
public class Container<B> implements Serializable {
    B data;
}
