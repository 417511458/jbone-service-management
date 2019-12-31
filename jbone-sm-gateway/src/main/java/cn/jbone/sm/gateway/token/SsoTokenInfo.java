package cn.jbone.sm.gateway.token;

import cn.jbone.sso.common.domain.UserInfo;
import lombok.Data;

@Data
public class SsoTokenInfo {
    private String service;
    private String id;
    private String clientId;
    private UserInfo userInfo;
}
