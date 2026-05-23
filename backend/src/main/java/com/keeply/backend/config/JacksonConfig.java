/*
 * Classe de configuração do Jackson para processamento de JSON.
 * Configura o bean ObjectMapper utilizado pelo Spring Boot, registrando o módulo JavaTimeModule
 * para suporte adequado aos tipos de data e hora do Java 8 e buscando módulos adicionais.
 */
package com.keeply.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .findAndRegisterModules();
    }
}
