package com.geovannycode.ecoeats

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationPropertiesScan
@SpringBootApplication
class EcoEatsApplication

fun main(args: Array<String>) {
	runApplication<EcoEatsApplication>(*args)
}
