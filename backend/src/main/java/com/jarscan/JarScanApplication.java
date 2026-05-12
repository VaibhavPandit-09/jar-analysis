package com.jarscan;

import com.jarscan.config.JarScanProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JarScanProperties.class)
public class JarScanApplication {

    public static void main(String[] args) {
        SpringApplication.run(JarScanApplication.class, args);
    }
}
