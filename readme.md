# Cloud Server Ip Range Utility

------------

## Intro

Convert the lists of IP addresses published by each cloud server provider into regular expressions, allowing the application to distinguish whether an IP is a Cloud Server IP.

> Both IPv4 and IPv6 are supported. <br>
If you have any thoughts on contributing to the improvement of features, feel free to reach out anytime.

## Usage

When using in application, use 'isServerIP' function within 'IPRangeUtil'.

```kotlin
import com.devlee.ipranges.util.IPRangeUtil

class Test {

    fun validateIP(ip: String?): Boolean {
        return IPRangeUtil.isServerIP(ip)
    }
    
}
```

<br>

You can also apply filters, such as by cloud provider or cloud region.

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

<br>

If you need details about which provider, region, and pattern matched, use 'findMatch'.

```kotlin
import com.devlee.ipranges.util.IPRangeUtil
import com.devlee.ipranges.core.provider.Provider

class Test {

    fun describeIP(ip: String?) {
        val match = IPRangeUtil.findMatch(ip) ?: return

        println("provider=${match.provider}, region=${match.region}, pattern=${match.matchedPattern}")
    }

}
```
