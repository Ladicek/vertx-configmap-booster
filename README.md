# Quickstart - Vert.x - Kubernetes Config Map

Quickstart where the Vert.x container retrieves the Application parameters using a Kubernetes ConfigMap. For this quickstart, we will instantiate a Vertx HTTP Server
where the port number has been defined using a Kubernetes configMap using this name `app-config`. 

```java
 ConfigurationStoreOptions appStore = new ConfigurationStoreOptions();
 appStore.setType("configmap")
         .setConfig(new JsonObject()
                 .put("namespace", "vertx-demo")
                 .put("name", "app-config")); / Name of the ConfigMap to be fetched 

 configurationservice = ConfigurationService.create(vertx, new ConfigurationServiceOptions()
         .addStore(appStore));
 Router router = Router.router(vertx);

 router.route().handler(BodyHandler.create());
 router.get("/products/:productID").handler(this::handleGetProduct);
 router.put("/products/:productID").handler(this::handleAddProduct);
 router.get("/products").handler(this::handleListProducts);

 configurationservice.getConfiguration(ar -> {
     // We can retrieve the port key from the cache created when the Configuration Service has retrieve the values from Kubernetes
     int port = configurationservice.getCachedConfiguration().getInteger("port") != null ? configurationservice.getCachedConfiguration().getInteger("port") : 8080;
     LOGGER.info("Config Map - port : " + port);
     vertx.createHttpServer().requestHandler(router::accept).listen(port);
 });
```

The config map contains under the `app.json` key, the list of the key/value pairs defined 
using JSon as Dataformat for our application as you can see hereafter :

```yml
apiVersion: v1
data:
  app.json: |-
    {
        "logging":"debug",
        "hostname":"127.0.0.1",
        "port":"8080"
    }
kind: ConfigMap
metadata:
  creationTimestamp: 2016-09-14T12:13:08Z
  name: app-config
  namespace: vertx-demo
  resourceVersion: "6232"
  selfLink: /api/v1/namespaces/vertx-demo/configmaps/app-config
  uid: 990dd236-7a74-11e6-8d26-868f517b6834
```


# Instructions

* Launch minishift

```
minishift start --deploy-registry=true --deploy-router=true --memory=4048 --vm-driver="xhyve"
eval $(minishift docker-env)
```
   
* Log on to openshift
```    
oc login $(minishift ip):8443 -u admin -p admin -n default
```    
# Create a new project

```    
oc new-project vertx-demo
oc policy add-role-to-user view openshift-dev -n vertx-demo
oc policy add-role-to-group view system:serviceaccounts -n vertx-demo
```

# Create the ConfigMap

```
oc create configmap app-config --from-file=src/main/resources/app.json
```

# To consult the configMap (optional)

```
oc get configmap/app-config -o yaml
```

* Build and deploy the project
   
```
mvn -Popenshift   
```

# Consult the Service deployed 

First, we must retrieve the IP Address of the service exposed by the OpenShift Router to our host machine

```
export service=$(minishift service simple-vertx-configmap -n vertx-demo --url=true)
http://192.168.64.12:32741
```

Next, we can use curl or httpie tool to fetch the products from the REST service

```
curl $service/products
HTTP/1.1 200 OK
Content-Length: 252
content-type: application/json

[
    {
        "id": "prod7340",
        "name": "Tea Cosy",
        "price": 5.99,
        "weight": 100
    },
    {
        "id": "prod3568",
        "name": "Egg Whisk",
        "price": 3.99,
        "weight": 150
    },
    {
        "id": "prod8643",
        "name": "Spatula",
        "price": 1.0,
        "weight": 80
    }
]
```

We can also grab the info about one product

```
curl $service/products/prod7340
HTTP/1.1 200 OK
Content-Length: 82
content-type: application/json

{
    "id": "prod7340",
    "name": "Tea Cosy",
    "price": 5.99,
    "weight": 100
}
```

or create a new product

```
curl -X PUT $service/products/prod1122 -d @src/main/resources/prod1122.json
```

# Get the log of the Container 

```
bin/oc-log.sh simple-config-map
```

# Update the port number

The Vertx Configuration Service provides a listener which can be informed if a config parameter of the ConfigMap has changed. The listener checks every 5s if such a modification occured. 

```java
conf.listen((newConf -> {
  int port = newConf.getInteger("port", 8080);
  httpServer.close();

  httpServer.requestHandler(router::accept).listen(port);
```

To test this feature, you will edit first the configMap and change the port number from the value `8080` to `9090`. 

```
oc edit configmap/app-config
```

Next, we will check the log of the pod to verify that the modification has been propagated to the listener of Vertx.

```
bin/oc-log.sh simple-config-map
... 
New configuration: {
  "logging" : "debug",
  "hostname" : "127.0.0.1",
  "port" : 9090
}
2016-09-15 12:16:50 INFO  SimpleRest:80 - Port has changed: 9090
2016-09-15 12:16:50 INFO  SimpleRest:84 - The HttpServer will be stopped and restarted. 
```

If the log reports that the port number is now `9090` and that the HTTP Server has been restarted, then we can edit the OpenShift Service which maps the internal port number used by the Docker container
and exposed by the pod.

```
oc edit service/simple-vertx-configmap
... 
- nodePort: 30740
  port: 8080
  protocol: TCP
  targetPort: 8080

-->

- nodePort: 30740
  port: 8080
  protocol: TCP
  targetPort: 9090

```

Call again the REST endpoint to get all the products

```
export service=$(minishift service simple-vertx-configmap -n vertx-demo --url=true)
curl $service/products
[ {
  "id" : "prod7340",
  "name" : "Tea Cosy",
  "price" : 5.99,
  "weight" : 100
}, {
  "id" : "prod3568",
  "name" : "Egg Whisk",
  "price" : 3.99,
  "weight" : 150
}, 
...
```
# Steps to execute to play with the demo

```
eval $(minishift docker-env)
oc login $(minishift ip):8443 -u admin -p admin -n default

oc delete project vertx-demo
oc new-project vertx-demo

oc policy add-role-to-user view openshift-dev -n vertx-demo
oc policy add-role-to-group view system:serviceaccounts -n vertx-demo

oc delete configmap/app-config
oc create configmap app-config --from-file=src/main/resources/app.json

docker rmi -f vertx-demo/simple-config-map:1.0.0-SNAPSHOT

mvn -Popenshift

export service=$(minishift service simple-vertx-configmap -n vertx-demo --url=true)

http $service/products
http $service/products/prod7340
http -X PUT $service/products/prod1122 -d @src/main/resources/prod1122.json

bin/oc-log.sh simple-config-map

oc get configmap/app-config -o yaml
oc edit configmap/app-config

bin/oc-log.sh simple-config-map

oc edit service/simple-vertx-configmap

export service=$(minishift service simple-vertx-configmap -n vertx-demo --url=true)
http $service/products
```

# Troubleshooting

## Delete Replication controller, service, ConfigMap

```
oc delete configmap/app-config

oc delete service simple-vertx-configmap
oc delete rc simple-config-map

oc edit configmap/app-config
```

