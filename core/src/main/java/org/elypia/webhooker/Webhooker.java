/*
 * Copyright 2019-2020 Elypia CIC and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elypia.webhooker;

import com.google.gson.Gson;
import org.elypia.webhooker.controller.*;
import org.slf4j.*;
import spark.Spark;

import java.io.Closeable;
import java.net.*;
import java.util.Objects;

import static spark.Spark.*;

/**
 * This class should be used as a singleton.
 * There is no need to create multiple web servers.
 *
 * @author seth@elypia.org (Syed Shah)
 */
public class Webhooker implements Closeable {

    /** SLF4J logger. */
    private static final Logger logger = LoggerFactory.getLogger(Webhooker.class);

    /**
     * The public url the payloads can be made to. This could be the server
     * IP appended with the port of Spark, something else if using a
     * reverse proxy. This defaults to <code>http://localhost:{@link Spark#port()}/</code>
     *
     * This is used purely for clients to know the public callback
     * url that can be provided to external services.
     */
    private final String publicUrl;

    /**
     * A means to manage clients that are subscribed though Webhooker.
     * This is managed though an interface so it's easy to implement
     * a means to control clients that works best for your application such
     * as database, redis, or in memory (default).
     */
    private final ClientController controller;

    /**
     * The route that is handling payloads and mapping
     * them to the correct client.
     */
    private final WebhookRoute route;

    /** A {@link Gson} instance for serializing and deserializing JSON. */
    private final Gson gson;

    public Webhooker(String publicUrl) throws MalformedURLException {
        this(publicUrl, -1);
    }

    public Webhooker(String publicUrl, int port) throws MalformedURLException {
        this(publicUrl, port, new MapClientController());
    }

    public Webhooker(String publicUrl, int port, ClientController controller) throws MalformedURLException {
        this(publicUrl, port, controller, new Gson());
    }

    /**
     * Create an instance of Webhooker.
     *
     * @param publicUrl The public URL public services are expected to POST to.
     *                  This must include a route parameter <code>/:uuid</code>.
     * @param port The port to run the webserver, this defaults to {@link Spark}s
     *             default if set to -1, a random port if 0, else the specified port.
     * @param controller The controller to manager clients.
     * @param gson The Gson (de)serializer for converting objects from JSON to POJO.
     * @throws MalformedURLException If publicUrl is not a valid URL.
     */
    public Webhooker(String publicUrl, int port, ClientController controller, Gson gson) throws MalformedURLException {
        if (port < -1)
            throw new IllegalArgumentException("`port` can not be below -1.");

        URL url = new URL(publicUrl);
        String protocol = url.getProtocol();

        if (!protocol.equals("http") && !protocol.equals("https"))
            throw new IllegalArgumentException("`publicUrl` must start with HTTP or HTTPS.");

        String path = url.getPath();

        if (!path.contains("/:uuid"))
            throw new IllegalArgumentException("`publicUrl` must have path parameter :uuid, for example `https://webhook.elypia.org/:uuid`.");

        this.controller = Objects.requireNonNull(controller);
        this.gson = Objects.requireNonNull(gson);
        this.publicUrl = publicUrl;

        if (port == 0)
            logger.warn("It's not ideal not to set `port` to 0 as using a random port can make it difficult to persist webhooks urls or have a reliable environment.");

        if (port != -1)
            port(port);

        route = new WebhookRoute(this);
        post(path, route);
    }

    public Gson getGson() {
        return gson;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getPublicUrl(Client client) {
        return publicUrl.replace(":uuid", client.getUuid().toString());
    }

    public ClientController getController() {
        return controller;
    }

    public WebhookRoute getRoute() {
        return route;
    }

    @Override
    public void close() {
        stop();
    }
}
