package com.devlee.ipranges.core.io

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*

class RequestClient private constructor() {

    companion object {

        private val lazyClient: HttpClient by lazy {
            HttpClient(Java) {
                install(ContentNegotiation) {
                    json()
                }
            }
        }

        fun getClient(): HttpClient = lazyClient

        suspend inline fun <reified T>getResponse(url: String): T
            = getClient().get(url).body<T>()

    }

}
