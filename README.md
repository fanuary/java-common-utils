# java-common-utils
some useful common-utils for Java

## HttpUtils
pom依赖：

```xml
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
<version>4.5.13</version>
</dependency>
    <dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpmime</artifactId>
    <version>4.5.13</version>
</dependency>
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpcore</artifactId>
    <version>4.4.16</version>
</dependency>
```

------

示例：

1. 普通请求

   ```java
   HttpRequestConfig config = HttpRequestConfig.builder()
        .url("https://api.example.com/users")
        .build();
   String response = HttpUtils.get(config);
   ```

2. 请求加代理

   ```java
   HttpUtils.ProxyConfig proxyConfig = HttpUtils.ProxyConfig.builder()
       .host("127.0.0.1")
       .port(7890)
       .scheme("http")
       .username("proxyuser")  // 可选
       .password("proxypass")  // 可选
       .build();
   
   HttpUtils.HttpRequestConfig config = HttpUtils.HttpRequestConfig.builder()
       .url("https://www.google.com/")
       .proxyConfig(proxyConfig)
       .build();
   String response = HttpUtils.get(config);
   System.out.println(response);
   ```

   
