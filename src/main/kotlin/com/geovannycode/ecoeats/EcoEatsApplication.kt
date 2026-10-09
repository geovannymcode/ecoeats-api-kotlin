package com.geovannycode.ecoeats

import com.geovannycode.ecoeats.shared.config.ProductionEnvironmentValidator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class EcoEatsApplication

fun main(args: Array<String>) {
    runApplication<EcoEatsApplication>(*args) { addInitializers(ProductionEnvironmentValidator()) }
}
