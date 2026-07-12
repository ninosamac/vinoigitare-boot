package com.vinoigitare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: needed by com.vinoigitare.storage.SongImporter's
// periodic .tab-file reconciliation (see its Javadoc) -- nothing else in
// the app uses Spring's scheduler.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class VinoigitareBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(VinoigitareBootApplication.class, args);
	}

}
