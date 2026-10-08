package com.project.qms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Quality Management System.
 *
 * Running this class starts an embedded Tomcat server on port 8080 and
 * connects to MySQL using the settings in application.properties.
 */
@SpringBootApplication
public class QmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(QmsApplication.class, args);
    }
}
