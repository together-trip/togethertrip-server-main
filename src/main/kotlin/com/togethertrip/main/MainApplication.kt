package com.togethertrip.main

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class MainApplication

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
}
