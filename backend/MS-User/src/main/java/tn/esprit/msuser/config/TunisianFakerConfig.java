package tn.esprit.msuser.config;



import com.github.javafaker.Faker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class TunisianFakerConfig {

    @Bean
    public Faker tunisianFaker() {
        // Utilisation de la locale française (proche du tunisien)
        return new Faker(new Locale("fr"));
    }

    @Bean
    public Faker arabicFaker() {
        // Pour les données en arabe
        return new Faker(new Locale("ar"));
    }
}