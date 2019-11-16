package cn.jbone.sm.registry;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class JboneSmRegistryInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder
                .sources(JboneSmRegistryApplication.class)
                .banner(new JboneSmRegistryBanner())
                .logStartupInfo(true);
    }
}
