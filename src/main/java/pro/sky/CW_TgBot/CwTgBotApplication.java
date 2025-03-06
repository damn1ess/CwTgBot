package pro.sky.CW_TgBot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CwTgBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(CwTgBotApplication.class, args);
	}

}
