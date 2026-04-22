package com.aicsp.stream;

import com.aicsp.stream.config.PythonEngineProperties;
import com.aicsp.stream.config.StreamSseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PythonEngineProperties.class, StreamSseProperties.class})
public class StreamServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamServiceApplication.class, args);
    }
}
