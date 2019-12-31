package cn.jbone.sm.gateway.config;

import cn.jbone.sm.gateway.filters.IpFilter;
import cn.jbone.sm.gateway.filters.TokenFilter;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Collections;
import java.util.List;

/**
 * 跨域配置
 */
@Configuration
public class GateWayConfiguration {

    @Bean
    public CorsFilter corsFilter() {
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        final CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true); // 允许cookies跨域
        config.addAllowedOrigin("http://cmsadmin.local.jbone.cn");// #允许向该服务器提交请求的URI，*表示全部允许，在SpringMVC中，如果设成*，会自动转成当前请求头中的Origin
        config.addAllowedHeader("*");// #允许访问的头信息,*表示全部
        config.setMaxAge(18000L);// 预检请求的缓存时间（秒），即在这个时间段里，对于相同的跨域请求不会再预检了
        config.addAllowedMethod("*");// 允许提交请求的方法，*表示全部允许
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Configuration
    @ConfigurationProperties(prefix = "jbone.gateway.auth")
    @Data
    public static class TokenFilterConfiguration{
        private List<String> uriWhiteList;

        private String ssoServiceId = "jbone-sso-server";

        @Bean
        public TokenFilter tokenFilter(ProxyRequestHelper helper, @Qualifier("ribbonCommandFactory") RibbonCommandFactory<?> ribbonCommandFactory){
            return new TokenFilter(helper,ribbonCommandFactory,Collections.emptyList(),uriWhiteList,ssoServiceId);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "jbone.gateway.auth.ip",name = "enabled",havingValue = "true")
    @ConfigurationProperties(prefix = "jbone.gateway.auth.ip")
    @Data
    public static class IpFilterConfiguration{
        private List<String> blackList;
        @Bean
        public IpFilter ipFilter(){
            return new IpFilter(blackList);
        }

    }

}
