# Cloud Server IP Range Utility

Detect whether an IP address belongs to a major cloud provider — offline, with no API calls at lookup time.

This library packs the IP range lists published by each cloud provider into a compact binary lookup table, bundled with the library, so your application can answer *"is this a cloud server IP?"* with a local binary search.

> Both **IPv4 and IPv6** are supported.

## Features

- **Offline lookup** — range data ships inside the jar; no network access at runtime
- **IPv4 + IPv6** — full CIDR support at any prefix length
- **Fast** — binary search over ~50,000 blocks: sub-millisecond lookups, ~2 MB of heap
- **Provider / region filtering** — restrict matching to one provider or one region
- **Detailed match results** — `findMatch` tells you which provider, region, and CIDR block matched
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
    implementation("com.github.developerlee79:server-ip-ranges:v1.2.0")
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
    implementation 'com.github.developerlee79:server-ip-ranges:v1.2.0'
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

1. Each provider's published range document is parsed into region-grouped CIDR blocks and committed as `range/<provider>/ip-range.json` — the only range data kept in git.
2. At build time the Gradle `packRangeData` task turns each file into `ranges.bin`, a sorted table of network addresses plus prefix lengths, and that table is what ships in the jar. End addresses are implied by the prefix length, and IPv4 blocks are stored in four bytes.
3. At runtime the table is loaded once per provider and cached — from `./range/<provider>/ip-range.json` when running inside a repo checkout, otherwise from the packed resource in the jar.
4. `isServerIP` / `findMatch` parse the input into an unsigned 32-bit or 128-bit integer and binary-search the table.

Providers publish overlapping and nested blocks, so a match walks back from the binary-search position while an enclosing block is still possible. Where blocks overlap, the most specific one is reported.

## Updating Range Data

Providers change their ranges over time. Regenerate the data from a repo checkout:

```bash
./gradlew updateRangeFiles
```

This fetches every provider's live feed, rewrites `range/*/ip-range.json`, and reports per-provider failures without aborting the whole run. The packed tables are regenerated by the next build; run `./gradlew packRangeData` to refresh them on their own. The default `./gradlew test` task is hermetic and never touches the network.

## Contributing

Issues and pull requests welcome — provider additions, data corrections, and feature ideas alike.

## License

[Apache License 2.0](LICENSE)
