package com.edu.springsecuritydemo.utils;
// Java标准库导入

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Apache HttpComponents导入
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
//import org.apache.http.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.util.EntityUtils;

// SSL相关导入
import javax.net.ssl.SSLContext;

// Lombok注解导入
import lombok.Builder;
import lombok.Getter;

// 可选：日志相关导入
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 不加代理的用法：
 * HttpRequestConfig config = HttpRequestConfig.builder()
 *     .url("https://api.example.com/users")
 *     .build();
 * String response = HttpUtils.get(config);
 *
 * 加代理的用法：
 * ProxyConfig proxyConfig = ProxyConfig.builder()
 *     .host("proxy.example.com")
 *     .port(8080)
 *     .scheme("http")
 *     .username("proxyuser")  // 可选
 *     .password("proxypass")  // 可选
 *     .build();
 *
 * HttpRequestConfig config = HttpRequestConfig.builder()
 *     .url("https://api.example.com/users")
 *     .proxyConfig(proxyConfig)
 *     .build();
 * String response = HttpUtils.get(config);
 */

public class HttpUtils {
    private static final String UTF8 = "utf-8";
    private static final int BYTE_ARRAY_LENGTH = 1024 * 1024;
    private static final CloseableHttpClient defaultHttpClient;
    private static final CloseableHttpClient defaultSslHttpClient;

    // 初始化默认HTTP客户端
    static {
        defaultHttpClient = HttpClients.createDefault();
        defaultSslHttpClient = createSSLHttpClient(null);
    }

    // 代理配置类
    @Builder
    @Getter
    public static class ProxyConfig {
        private final String host;
        private final int port;
        private final String username;
        private final String password;
        private final String scheme; // http or https

        public HttpHost toHttpHost() {
            return new HttpHost(host, port, scheme);
        }
    }

    // 请求配置类
    @Builder
    @Getter
    public static class HttpRequestConfig {
        private final String url;
        private final Map<String, String> headers;
        private final Map<String, String> params;
        private final String jsonBody;
        private final int connectionTimeout;
        private final int readTimeout;
        private final ProxyConfig proxyConfig;
    }

    // 自定义异常类
    public static class HttpRequestException extends Exception {
        public HttpRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static String buildUrl(String url, Map<String, String> params) throws Exception {
        if (params == null || params.isEmpty()) {
            return url;
        }

        URIBuilder builder = new URIBuilder(url);
        params.forEach(builder::setParameter);
        return builder.build().toString();
    }


    private static HttpEntity buildFormEntity(Map<String, String> params) {
        List<BasicNameValuePair> pairs = params.entrySet().stream()
                .map(e -> new BasicNameValuePair(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        try {
            return new UrlEncodedFormEntity(pairs, UTF8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to create form entity", e);
        }
    }

    public static String get(HttpRequestConfig config) throws HttpRequestException {
        try {
            HttpGet get = new HttpGet(buildUrl(config.getUrl(), config.getParams()));
            return executeRequest(get, config);
        } catch (Exception e) {
            throw new HttpRequestException("Failed to execute GET request", e);
        }
    }

    public static String post(HttpRequestConfig config) throws HttpRequestException {
        try {
            HttpPost post = new HttpPost(config.getUrl());
            if (config.getJsonBody() != null) {
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(config.getJsonBody(), UTF8));
            } else if (config.getParams() != null) {
                post.setEntity(buildFormEntity(config.getParams()));
            }
            return executeRequest(post, config);
        } catch (Exception e) {
            throw new HttpRequestException("Failed to execute POST request", e);
        }
    }

    // 创建带代理的HTTP客户端
    private static CloseableHttpClient createHttpClientWithProxy(ProxyConfig proxyConfig) {
        HttpClientBuilder builder = HttpClients.custom();

        if (proxyConfig != null) {
            // 设置代理主机
            HttpHost proxy = proxyConfig.toHttpHost();
            builder.setProxy(proxy);

            // 如果有代理认证信息，设置认证
            if (proxyConfig.getUsername() != null && proxyConfig.getPassword() != null) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(proxy),
                        new UsernamePasswordCredentials(proxyConfig.getUsername(), proxyConfig.getPassword())
                );
                builder.setDefaultCredentialsProvider(credentialsProvider);
            }
        }

        return builder.build();
    }

    // 创建带代理的HTTPS客户端
    private static CloseableHttpClient createSSLHttpClient(ProxyConfig proxyConfig) {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(new TrustSelfSignedStrategy())
                    .build();

            SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE
            );

            HttpClientBuilder builder = HttpClients.custom()
                    .setSSLSocketFactory(sslFactory);

            if (proxyConfig != null) {
                HttpHost proxy = proxyConfig.toHttpHost();
                builder.setProxy(proxy);

                if (proxyConfig.getUsername() != null && proxyConfig.getPassword() != null) {
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(
                            new AuthScope(proxy),
                            new UsernamePasswordCredentials(proxyConfig.getUsername(), proxyConfig.getPassword())
                    );
                    builder.setDefaultCredentialsProvider(credentialsProvider);
                }
            }

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL HTTP client", e);
        }
    }

    private static void setHeaders(HttpUriRequest request, Map<String, String> headers) {
        Map<String, String> requestHeaders = headers != null ? headers : getDefaultHeaders();
        requestHeaders.forEach(request::setHeader);
    }

    private static Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Connection", "keep-alive");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Accept", "*/*");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.135 Safari/537.36");
        return headers;
    }


    private static String executeRequest(HttpUriRequest request, HttpRequestConfig config) throws Exception {
        setHeaders(request, config.getHeaders());

        // 根据是否配置代理选择相应的HttpClient
        CloseableHttpClient client;
        if (config.getProxyConfig() != null) {
            client = request.getURI().toString().startsWith("https:") ?
                    createSSLHttpClient(config.getProxyConfig()) :
                    createHttpClientWithProxy(config.getProxyConfig());
        } else {
            client = request.getURI().toString().startsWith("https:") ?
                    defaultSslHttpClient :
                    defaultHttpClient;
        }

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 400) {
                throw new HttpRequestException(
                        String.format("Request failed with status %d", statusCode),
                        null
                );
            }

            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity, UTF8);
            }
            return "";
        }
    }

    // 其他方法保持不变...

    // 使用示例
    public static void main(String[] args) {
        try {
            // 创建代理配置
            ProxyConfig proxyConfig = ProxyConfig.builder()
                    .host("proxy.example.com")
                    .port(8080)
                    .scheme("http")
                    .username("proxyuser")  // 可选
                    .password("proxypass")  // 可选
                    .build();

            // GET请求示例（使用代理）
            HttpRequestConfig getConfig = HttpRequestConfig.builder()
                    .url("https://api.example.com/users")
                    .proxyConfig(proxyConfig)
                    .build();
            String getResponse = get(getConfig);

            // POST请求示例（使用代理）
            Map<String, String> params = new HashMap<>();
            params.put("name", "John");
            HttpRequestConfig postConfig = HttpRequestConfig.builder()
                    .url("https://api.example.com/users")
                    .params(params)
                    .proxyConfig(proxyConfig)
                    .build();
            String postResponse = post(postConfig);

            // 不使用代理的请求
            HttpRequestConfig noProxyConfig = HttpRequestConfig.builder()
                    .url("https://api.example.com/users")
                    .build();
            String noProxyResponse = get(noProxyConfig);

        } catch (HttpRequestException e) {
            e.printStackTrace();
        }
    }
}