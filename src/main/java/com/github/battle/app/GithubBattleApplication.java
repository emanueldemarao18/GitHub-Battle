package com.github.battle.app;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class GithubBattleApplication {

	public GithubBattleApplication(@Value("${github.token}") String githubToken) {
		System.out.println("GITHUB_TOKEN : " +
				(githubToken != null && !githubToken.isBlank() && !githubToken.equals("missing-token")
						? "SIM (" + githubToken.substring(0, 4) + "...)"
						: "NÃO"));
	}

	public static void main(String[] args) {
		new SpringApplicationBuilder(GithubBattleApplication.class)
				.initializers(new DotenvApplicationInitializer())
				.run(args);
	}

}
