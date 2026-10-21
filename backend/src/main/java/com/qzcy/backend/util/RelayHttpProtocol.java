package com.qzcy.backend.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/** Plain HTTP upstreams such as Uvicorn may reject Java's automatic h2c upgrade. */
public final class RelayHttpProtocol {
    private RelayHttpProtocol() { }

    public static HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            builder.version(HttpClient.Version.HTTP_1_1);
        }
        return builder;
    }
}
