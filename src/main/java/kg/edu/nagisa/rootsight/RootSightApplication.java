package kg.edu.nagisa.rootsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RootSightApplication {

    /**
     * 启动 RootSight Web 后端及其 Spring 容器。
     */
    public static void main(String[] args) {
        SpringApplication.run(RootSightApplication.class, args);
    }

}
