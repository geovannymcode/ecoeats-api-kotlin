package com.geovannycode.ecoeats

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<EcoEatsApplication>().with(TestcontainersConfiguration::class).run(*args)
}
