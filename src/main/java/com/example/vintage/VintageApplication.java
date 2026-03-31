package com.example.vintage;

import com.example.vintage.config.DifyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({DifyProperties.class})
public class VintageApplication {

    public static void main(String[] args) {
        SpringApplication.run(VintageApplication.class, args);
    }

}
