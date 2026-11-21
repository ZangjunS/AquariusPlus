package com.ds.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 每个服务独立暴露 knife4j 端点（路径由 spring.application.name 决定）。
 * 网关聚合各服务文档，见 ds-gateway 配置。
 */
@Configuration
public class Knife4jConfig {

    @Value("${spring.application.name:ds}")
    private String appName;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title(appName + " API")
                        .description(appName + " 接口文档（Knife4j / OpenAPI3）")
                        .version("v1")
                        .contact(new Contact().name(appName))
                        .license(new License().name("Apache 2.0")));
    }
}
