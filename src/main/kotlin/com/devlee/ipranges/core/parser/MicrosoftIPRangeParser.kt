package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.RequestClient
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.io.model.ProviderInfo
import com.devlee.ipranges.core.provider.Provider
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data object MicrosoftIPRangeParser : IPRangeParser {

    /*
    * Azure is the only provider without a stable feed URL: the file name carries a date and
    * sits behind a download page that has to be scraped. It is also the largest share of the
    * published data, so the discovered URL is validated rather than trusted -- the host is
    * pinned in the pattern, and the path cannot contain anything but URL path characters.
    */
    private val RANGE_FILE_URL_REGEX =
        "https://download\\.microsoft\\.com/download/[A-Za-z0-9/_.-]+/ServiceTags_Public_(\\d{8})\\.json".toRegex()

    private val FILE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /*
    * Azure republishes weekly. A document much older than that means the download page is
    * serving a stale link: the scrape "succeeds" and quietly republishes old ranges, which
    * no count-based check downstream can catch.
    */
    private const val MAXIMUM_DOCUMENT_AGE_DAYS = 45L

    override val provider: Provider
        get() = Provider.Microsoft

    override fun parse(): List<IPRanges> {
        val ipRangeMap = HashMap<String, MutableList<String>>()

        runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            val rangeFileURL = findRangeFileURL(providerInfo)
            requireFreshDocument(rangeFileURL, LocalDate.now())

            /*
            * Read into memory rather than through a scratch file: the document is a few MB,
            * and staging it under the working directory made parsing depend on where the
            * process happened to run.
            */
            val rangeDocument = RequestClient.getClient().get(rangeFileURL).bodyAsText()

            val rangeJson = Json.parseToJsonElement(rangeDocument).jsonObject

            val platformArray = rangeJson["values"]?.jsonArray

            if (platformArray.isNullOrEmpty()) {
                throw IllegalStateException("Microsoft range document contains no 'values' entries")
            }

            for (platform in platformArray) {
                val platformObject = platform.jsonObject

                val serviceName = platformObject["name"]!!.jsonPrimitive.content

                /*
                * Microsoft's IP range document includes the IP of management and other services.
                * In this project, we only parse the IP addresses of Azure Cloud Computing instances.
                */
                if (isNotServerInstance(serviceName)) {
                    continue
                }

                val prefixes = platformObject["properties"]!!.jsonObject["addressPrefixes"]!!.jsonArray

                for (prefix in prefixes) {
                    val ipAddress = prefix.jsonPrimitive.content
                    ipRangeMap.computeIfAbsent(serviceName) { mutableListOf() }.add(ipAddress)
                }
            }
        }

        return ipRangeMap.map { entry -> IPRanges(entry.key, entry.value) }.toList()
    }

    /*
    * Both pages carry the same link. The confirmation page is the one Microsoft's docs point
    * at, and details.aspx is where that redirect lands when it changes; trying the second
    * costs one request on a path that has already failed.
    */
    private suspend fun findRangeFileURL(provider: ProviderInfo): String {
        val attempted = mutableListOf<String>()

        for (pageUrl in candidatePageUrls(provider.url)) {
            attempted += pageUrl

            val page = runCatching { RequestClient.getClient().get(pageUrl).bodyAsText() }
                .getOrNull()
                ?: continue

            extractRangeFileURL(page)?.let { return it }
        }

        throw NoSuchElementException(
            "Cannot find the Azure ServiceTags download URL on any of: ${attempted.joinToString()}"
        )
    }

    internal fun candidatePageUrls(configuredUrl: String): List<String> =
        listOf(configuredUrl, configuredUrl.replace("confirmation.aspx", "details.aspx")).distinct()

    /** The newest ServiceTags link on the page, or null when the page carries none. */
    internal fun extractRangeFileURL(page: String): String? =
        RANGE_FILE_URL_REGEX.findAll(page)
            .maxByOrNull { it.groupValues[1] }
            ?.value

    /**
     * @throws IllegalStateException when the discovered document is older than
     * [MAXIMUM_DOCUMENT_AGE_DAYS], which is how a stale download page presents itself.
     */
    internal fun requireFreshDocument(rangeFileURL: String, today: LocalDate) {
        val publishedText = RANGE_FILE_URL_REGEX.find(rangeFileURL)?.groupValues?.get(1)
            ?: throw IllegalStateException("Unexpected Azure ServiceTags URL: $rangeFileURL")

        val published = try {
            LocalDate.parse(publishedText, FILE_DATE_FORMAT)
        } catch (error: DateTimeParseException) {
            throw IllegalStateException("Unparseable date in Azure ServiceTags URL: $rangeFileURL", error)
        }

        val age = ChronoUnit.DAYS.between(published, today)
        if (age > MAXIMUM_DOCUMENT_AGE_DAYS) {
            throw IllegalStateException(
                "Azure ServiceTags document is $age days old ($publishedText); " +
                    "the download page is serving a stale link"
            )
        }
    }

    private fun isNotServerInstance(serviceName: String) = !serviceName.startsWith("AzureCloud")

}
