/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.example.kubernetes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.example.util.Runner;
import io.vertx.ext.configuration.ConfigurationRetriever;
import io.vertx.ext.configuration.ConfigurationRetrieverOptions;
import io.vertx.ext.configuration.ConfigurationStoreOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SimpleRest extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleRest.class);

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        Runner.runExample(SimpleRest.class);
    }

    private Map<String, JsonObject> products = new HashMap<>();
    private ConfigurationRetriever conf;
    private HttpServer httpServer;

    @Override public void start() {

        setUpInitialData();
        setUpConfiguration();

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.get("/products/:productID").handler(this::handleGetProduct);
        router.put("/products/:productID").handler(this::handleAddProduct);
        router.get("/products").handler(this::handleListProducts);

        conf.getConfiguration(ar -> {
            if (ar.succeeded()) {
                int port = ar.result().getInteger("port", 8080);
                LOG.info("ConfigMap -> port : " + port);

                httpServer = vertx.createHttpServer();
                httpServer.requestHandler(router::accept).listen(port);
            } else {
                ar.cause().printStackTrace();
            }
        });

        conf.listen((configuration -> {
            LOG.info("Configuration change: " + configuration.toJson().encodePrettily());

            JsonObject newConfiguration = configuration.getNewConfiguration();
            int port = newConfiguration.getInteger("port", 8080);
            LOG.info("Port has changed: " + port);

            httpServer.close(event -> {
                if (event.succeeded()) {
                    LOG.info("The HttpServer will be stopped and restarted.");
                    httpServer.requestHandler(router::accept).listen(port);
                } else {
                    event.cause().printStackTrace();
                }
            });
        }));

    }

    private void handleGetProduct(RoutingContext routingContext) {
        String productID = routingContext.request().getParam("productID");
        HttpServerResponse response = routingContext.response();
        if (productID == null) {
            sendError(400, response);
        } else {
            JsonObject product = products.get(productID);
            if (product == null) {
                sendError(404, response);
            } else {
                response.putHeader("content-type", "application/json").end(product.encodePrettily());
            }
        }
    }

    private void handleAddProduct(RoutingContext routingContext) {
        String productID = routingContext.request().getParam("productID");
        HttpServerResponse response = routingContext.response();
        if (productID == null) {
            sendError(400, response);
        } else {
            JsonObject product = routingContext.getBodyAsJson();
            if (product == null) {
                sendError(400, response);
            } else {
                products.put(productID, product);
                response.end();
            }
        }
    }

    private void handleListProducts(RoutingContext routingContext) {
        JsonArray arr = new JsonArray();
        products.forEach((k, v) -> arr.add(v));
        routingContext.response().putHeader("content-type", "application/json").end(arr.encodePrettily());
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    private void setUpInitialData() {
        addProduct(new JsonObject().put("id", "prod3568").put("name", "Egg Whisk").put("price", 3.99)
                .put("weight", 150));
        addProduct(new JsonObject().put("id", "prod7340").put("name", "Tea Cosy").put("price", 5.99)
                .put("weight", 100));
        addProduct(new JsonObject().put("id", "prod8643").put("name", "Spatula").put("price", 1.00)
                .put("weight", 80));
    }

    private void setUpConfiguration() {
        ConfigurationStoreOptions appStore = new ConfigurationStoreOptions();
        appStore.setType("configmap")
                .setConfig(new JsonObject()
                        .put("namespace", "vertx-demo")
                        .put("name", "app-config")
                        .put("key","app.json"));

        conf = ConfigurationRetriever.create(vertx, new ConfigurationRetrieverOptions()
                .addStore(appStore));
    }

    private void addProduct(JsonObject product) {
        products.put(product.getString("id"), product);
    }
}