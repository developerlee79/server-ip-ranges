# Cloud Server IP Range Utility

Detect whether an IP address belongs to a major cloud provider, with no API call per lookup.

This library turns the IP range lists published by each cloud provider into a compact binary lookup table, so your application can answer *"is this a cloud server IP?"* with a local binary search.

**The range data does not live in this repository and does not ship in the jar.** It is built weekly by CI from the providers' own feeds and published as release assets. Your server downloads it once into a local cache and uses that copy from then on — so the data on your machine is yours, and it stays current without a library upgrade.

> Both **IPv4 and IPv6** are supported.

## Features

- **Local lookups** — after the one-time download, every lookup is a binary search against a cached table
- **Data you own** — tables live in a cache directory on your host, not inside the artifact
- **IPv4 + IPv6** — full CIDR support at any prefix length
- **Fast** — binary search over ~41,000 blocks, held as primitive arrays rather than objects
- **Provider / region filtering** — restrict matching to one provider or one region
- **Detailed match results** — `findMatch` tells you which provider, region, and CIDR block matched
- **Safe input handling** — never throws for bad input, never performs a DNS lookup
- **Verified downloads** — every table is checked against the SHA-256 published in `version.json`

## Supported Providers

| Provider | Source | IPv4 | IPv6 |
|----------|--------|------|------|
| Amazon (AWS) | [ip-ranges.amazonaws.com](https://ip-ranges.amazonaws.com/ip-ranges.json) | ✅ | ✅ |
| Microsoft (Azure) | ServiceTags (AzureCloud) | ✅ | ✅ |
| Google (GCP) | [cloud.json](https://www.gstatic.com/ipranges/cloud.json) | ✅ | ✅ |
| CloudFlare | [ips-v4 / ips-v6](https://www.cloudflare.com/ips/) | ✅ | ✅ |
| DigitalOcean | [geo feed](https://www.digitalocean.com/geo/google.csv) | ✅ | ✅ |
| Oracle (OCI) | [public_ip_ranges.json](https://docs.oracle.com/iaas/tools/public_ip_ranges.json) | ✅ | — |
| Tencent | Chat service IP list API | ✅ | — |

Oracle and Tencent publish IPv4-only feeds, and that is unlikely to change: Oracle hands out IPv6 as a per-VCN /56 rather than enumerating regional prefixes, so there is no authoritative list to consume. Third-party IPv6 sets for these providers are inferred from BGP announcements, which is fine for threat intelligence but not for answering "is this a cloud IP".

## Requirements

- JDK 11+
- Kotlin 1.9+ (for Kotlin consumers; works from Java as well)

## Installation

Published via [JitPack](https://jitpack.io). Add the repository and dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.developerlee79:server-ip-ranges:v2.0.0")
}
```

<details>
<summary>Groovy DSL</summary>

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// build.gradle
dependencies {
    implementation 'com.github.developerlee79:server-ip-ranges:v2.0.0'
}
```
</details>

## Usage

### Load the range data once at startup

No range data ships in the jar, so pick a source before the first lookup. Calling this on every start is fine: when the cache is current only the few-hundred-byte `version.json` is fetched.

```kotlin
import com.devlee.ipranges.util.IPRangeData

fun main() {
    // Downloads the published tables into ~/.cache/server-ip-ranges on first run.
    IPRangeData.useRelease()

    startApplication()
}
```

A lookup before this runs throws `IllegalStateException` rather than answering "not a cloud IP" — a missing dataset must not look like a negative result.

Hosts that must not reach the network can stage the release assets themselves:

```kotlin
IPRangeData.usePackedDirectory(File("/opt/server-ip-ranges"))
```

### Basic check

```kotlin
import com.devlee.ipranges.util.IPRangeUtil

class Test {

    fun validateIP(ip: String?): Boolean {
        return IPRangeUtil.isServerIP(ip)
    }

}
```

### Filter by provider or region

```kotlin
import com.devlee.ipranges.util.IPRangeUtil
import com.devlee.ipranges.core.provider.Provider

class Test {

    fun validateIP(ip: String?): Boolean {
        return IPRangeUtil.isServerIP(ip, Provider.Amazon)
    }

    fun validateIPWithRegion(ip: String?, region: String): Boolean {
        return IPRangeUtil.isServerIP(ip, Provider.Amazon, region)
    }

}
```

### Detailed match information

```kotlin
import com.devlee.ipranges.util.IPRangeUtil

class Test {

    fun describeIP(ip: String?) {
        val match = IPRangeUtil.findMatch(ip) ?: return

        println("provider=${match.provider}, region=${match.region}, range=${match.matchedRange}")
    }

}
```

### Input contract

- `null`, blank, and non-IP-literal input (including hostnames) return `false` / `null` — no exception is thrown for bad input.
- Hostnames are rejected **before** any `InetAddress` call, so no DNS lookup is ever performed.
- Addresses are normalized before matching: compressed IPv6 (`2001:db8::1`), uppercase hex, and IPv4-mapped IPv6 (`::ffff:1.2.3.4`) all work.
- IPv4 octets with leading zeros (`192.0.2.01`) are rejected, since readers disagree on whether `010` means decimal 10 or octal 8. Send `192.0.2.1`.
- A `false` result covers both "not a cloud IP" and "unparseable input"; use `findMatch` plus your own validation when you need to distinguish them.

## How It Works

1. Weekly, a GitHub Actions job fetches each provider's published range document and parses it into region-grouped CIDR blocks. Nothing is committed — the parsed JSON is a build artifact. Azure is the one provider with no stable feed URL, so its link is scraped from the download page; the scrape only accepts a `download.microsoft.com` URL and rejects a document older than 45 days, since a page stuck on an old link would otherwise republish stale ranges without any count changing enough to notice.
2. The same job packs each provider into `<provider>.bin`: a sorted table of network addresses plus prefix lengths. End addresses are implied by the prefix length, and IPv4 blocks are stored in four bytes. A provider whose block count collapses fails the job instead of being published, since that is what a truncated upstream feed looks like.
3. The tables and a `version.json` naming their SHA-256 digests are attached to a dated release.
4. `IPRangeData.useRelease()` downloads them into a cache directory, verifying each digest, and writes them into place atomically. Later starts re-fetch only `version.json`.
5. `isServerIP` / `findMatch` parse the input into an unsigned 32-bit or 128-bit integer and binary-search the cached table.

Providers publish overlapping and nested blocks, so a match walks back from the binary-search position while an enclosing block is still possible. Where blocks overlap, the most specific one is reported.

## Range Data

Data lives in three places, none of them this repository:

| Where | What | Who writes it |
|-------|------|---------------|
| Release assets | `<provider>.bin`, `version.json` | CI, weekly |
| `~/.cache/server-ip-ranges/<version>/` | verified copy of the tables | `IPRangeData.useRelease()` |
| `<dataDir>/range/<provider>/ip-range.json` | parsed feed output | `./gradlew updateRangeFiles` |

### Cache location

`$XDG_CACHE_HOME/server-ip-ranges`, falling back to `~/.cache/server-ip-ranges`. Pass a different directory to `useRelease(cacheDirectory = ...)`, or point `usePackedDirectory` at tables you staged yourself. A download that fails its digest check is discarded rather than cached.

### Working from a checkout

```bash
./gradlew updateRangeFiles   # fetch every provider feed into ./range
./gradlew packReleaseData    # pack build/release-data: <provider>.bin + version.json
```

`updateRangeFiles` reports per-provider failures without aborting the whole run, and sets `-Dipranges.dataDir` to the project directory so the fetched JSON is what lookups read. Set that property yourself to point a running application at a directory of JSON instead. The default `./gradlew test` task is hermetic — it builds its own fixtures and never touches the network.

Pass the previous manifest to arm the shrink guard when packing:

```bash
./gradlew packReleaseData -PpreviousVersionJson=previous-version.json
```

## Contributing

Issues and pull requests welcome — provider additions, data corrections, and feature ideas alike.

## License

[Apache License 2.0](LICENSE)
