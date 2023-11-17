# Cloud Server Ip Range Utility

------------

## Intro

Convert the lists of IP addresses published by each cloud server provider into regular expressions, allowing the application to distinguish whether an IP is a Cloud Server IP.

> Currently, only IPv4 is supported. <br>
If you have any thoughts on contributing to the improvement of features, such as adding IPv6 support, feel free to reach out anytime.

## Usage

When using in application, use 'isServerIP' function within 'IPRangeUtil'.

```kotlin
import com.devlee.ipranges.util.IPRangeUtil

class Test {

    fun validateIP(ip: String?) {
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

    fun validateIP(ip: String?) {
        return IPRangeUtil.isServerIP(ip, Provider.Amazon)
    }

    fun validateIPWithRegion(ip: String?, region: String) {
        return IPRangeUtil.isServerIP(ip, Provider.Amazon, region)
    }
    
}
```
