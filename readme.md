# Cloud Server IP Range Utility

Detect whether an IP address belongs to a major cloud provider — offline, with no API calls at lookup time.

This library converts the IP range lists published by each cloud provider into pre-generated regular expressions, bundled with the library, so your application can answer *"is this a cloud server IP?"* with a simple local match.

> Both **IPv4 and IPv6** are supported.

## Features

- **Offline lookup** — range data ships inside the jar; no network access at runtime
- **IPv4 + IPv6** — full CIDR support, including non-16-bit-aligned IPv6 prefixes
- **Provider / region filtering** — restrict matching to one provider or one region
- **Detailed match results** — `findMatch` tells you which provider, region, and pattern matched
- **Safe input handling** — never throws for bad input, never performs a DNS lookup
- **Refreshable data** — one Gradle task re-fetches every provider's published ranges

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

Oracle and Tencent publish IPv4-only feeds.

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
    implementation("com.github.developerlee79:server-ip-ranges:v1.0.0")
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
    implementation 'com.github.developerlee79:server-ip-ranges:v1.0.0'
}
```
</details>

## Usage

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

        println("provider=${match.provider}, region=${match.region}, pattern=${match.matchedPattern}")
    }

}
```

### Input contract

- `null`, blank, and non-IP-literal input (including hostnames) return `false` / `null` — no exception is thrown for bad input.
- Hostnames are rejected **before** any `InetAddress` call, so no DNS lookup is ever performed.
- Addresses are normalized before matching: compressed IPv6 (`2001:db8::1`), uppercase hex, and IPv4-mapped IPv6 (`::ffff:1.2.3.4`) all work.
- A `false` result covers both "not a cloud IP" and "unparseable input"; use `findMatch` plus your own validation when you need to distinguish them.

## How It Works

1. Each provider's published range document is parsed into region-grouped CIDR blocks (`range/<provider>/ip-range.json`).
2. Every CIDR block is converted into a regular expression that matches exactly the addresses inside the block (`range/<provider>/ip-regex.json`).
3. At runtime the pre-generated regexes are loaded once per provider (from the working directory if present, otherwise from the jar) and cached.
4. `isServerIP` / `findMatch` normalize the input address and test it against the cached patterns.

## Updating Range Data

Providers change their ranges over time. Regenerate all data files from a repo checkout:

```bash
./gradlew updateRangeFiles
```

This fetches every provider's live feed, rewrites `range/*/ip-range.json` and `range/*/ip-regex.json`, and reports per-provider failures without aborting the whole run. The default `./gradlew test` task is hermetic and never touches the network.

## Contributing

Issues and pull requests welcome — provider additions, data corrections, and feature ideas alike.

## License

[Apache License 2.0](LICENSE)
