package com.markerhub.shiro;

import lombok.Data;
import java.io.Serializable;

/**
 * @author mingchiuli
 * @create 2021-10-27 9:16 PM
 */
@Data
public class AccountProfile implements Serializable {

    private static final long serialVersionUID = -3207880482640325843L;

    private Long id;

    private String username;

    private String avatar;

    private String email;

}
