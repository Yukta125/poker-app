package com.sap.ase.poker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.ase.poker.security.JsonUsernamePasswordAuthenticationFilter;
import com.sap.ase.poker.security.JwtAuthenticationRequestFilter;
import com.sap.ase.poker.security.JwtTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableWebSecurity()
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private final ObjectMapper objectMapper;

    public WebSecurityConfig(ObjectMapper objectMapper) {
        super();
        this.objectMapper = objectMapper;
    }

    @Bean
    public UserDetailsService users() {
        User.UserBuilder userBuilder = User.builder()
                .passwordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder()::encode)
                .roles("USER");
        return new InMemoryUserDetailsManager(
                userBuilder.username("al-capone").password("all-in").build(),
                userBuilder.username("pat-garret").password("all-in").build(),
                userBuilder.username("wyatt-earp").password("all-in").build(),
                userBuilder.username("doc-holiday").password("all-in").build(),
                userBuilder.username("wild-bill").password("all-in").build(),
                userBuilder.username("stu-ungar").password("all-in").build(),
                userBuilder.username("kitty-leroy").password("all-in").build(),
                userBuilder.username("poker-alice").password("all-in").build(),
                userBuilder.username("madame-moustache").password("all-in").build()
        );
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        JwtTools jwtTools = new JwtTools(JwtTools.SECRET);
        http
                .authorizeRequests()
                .antMatchers("/table/**").authenticated()
                .antMatchers("/login/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .addFilter(new JwtAuthenticationRequestFilter(authenticationManager(), jwtTools))
                .addFilter(new JsonUsernamePasswordAuthenticationFilter(authenticationManager(), objectMapper, jwtTools))
                .formLogin(form -> form.loginPage("/login/index.html").permitAll())
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and().csrf().disable();
    }
}