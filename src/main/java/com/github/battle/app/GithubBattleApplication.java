package com.github.battle.app;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class GithubBattleApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GithubBattleApplication.class)
				.initializers(new DotenvApplicationInitializer())
				.run(args);
	}

}
