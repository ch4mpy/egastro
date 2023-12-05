package de.egastro.training.oidc.feign;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackageClasses = FeignConfiguration.class)
public class FeignConfiguration {

}
