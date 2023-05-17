# Custom Generic Proxy Interceptor

## Goal

To provide a generic way to Intercept http Traffic on the request and on the response paths without changing the applications of microservices or clients that are using them.


### Objectives
- Create a Generic Proxy Customized application that knows how to intercept all POST and GET methods.
- Redirect Traffic to this proxy using RH Service Mesh Istio.
- Present two modes of operations:
   1. Ambassador container inside a POD near application container
   2. Standalone Proxy in a dedicated POD.



### Procedure
Chose [JAVA Quarkus Framework](https://quarkus.io/) to build the Proxy Interceptor Application. \

#### Building Proxy Interceptor Image

1. First, Build the JAR file of proxy interceptor Application
```shell
 ./mvnw package
```

2. Using your favorite Containers CLI Tool (Docker/Podman/Buildah) to build the image:
```shell
podman build -f src/main/docker/Dockerfile.jvm -t registry-server-address.io/youraccount/traffic-interceptor-quarkus:1 .
```

3. Authenticate to your account at the registry and input your password once prompted:
```shell
podman login registry-server-address.io --username youruser
```
4. Push the image to registry:
```shell
podman push registry-server-address.io/youraccount/traffic-interceptor-quarkus:1
```


#### Custom Generic Proxy Implementation ( Reduced Code for overview reviewal):

1. Chose [JAVA Quarkus Framework](https://quarkus.io/) to build the Proxy Interceptor Application.
2. Defined A Controller class with Two Generic Java Methods Endpoints, that are being invoked by all POST and GET methods that clients requests from the application:
```java
@ApplicationScoped
@AllArgsConstructor
@Data
@Path("")
public class AllResources {
  //... code was reduced, only main parts for understanding remained.
    @Inject
    @Named("InvokeMicroserviceImpl")
    protected InvokeMicroservice invokeMicroservice;

    public AllResources() {
    }

    @ConfigProperty(name = "general.interceptor.mode")
    String interceptorMode;

    @ConfigProperty(name = "general.interceptor.servicePort")
    String servicePort;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{s:.*}")
    public Response handleGet(@HeaderParam("x-route-to") String routeTo, @HeaderParam("x-host-name") String hostName, @HeaderParam("x-port-no") String portNumber) {
        logger.info("got a get Response");
        ServiceParametersEntity serviceParametersEntity = setServiceParameters(hostName, portNumber);
// Invoke Microservice by rest client and return response , this will be intercepted by the ServerResponseFilter method in the filter class.
        MyResponseEntity response = invokeMicroservice.run(routeTo, serviceParametersEntity.getFinalPort(), serviceParametersEntity.getTransportAndHost());
        return Response.ok(response.getResponseBody()).replaceAll(response.getHeaders()).build();

    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{s:.*}")
    public Response handlePost(@HeaderParam("x-route-to") String routeTo, @HeaderParam("x-host-name") String hostName, @HeaderParam("x-port-no") String portNumber) {
        logger.info("got a POST Response");
        ServiceParametersEntity serviceParametersEntity = setServiceParameters(hostName, portNumber);
      // Invoke Microservice by rest client and return response , this will be intercepted by the ServerResponseFilter method in the filter class.
        MyResponseEntity response = invokeMicroservice.run(routeTo, serviceParametersEntity.getFinalPort(), serviceParametersEntity.getTransportAndHost());
        logger.infof("Response body from microservice : %s", response.getResponseBody());
        return Response.ok(response.getResponseBody()).replaceAll(response.getHeaders()).build();
    }
}
```
3. Using a ServerRequestFilter and ServerResponseFilter to define HTTP Filter that will intercept the request before the request, and after the response, in order to intercept and manipulate payload:
```java
@Data
public class WebFilter {
    //... part of the code ommited , only code essential for understanding remained.
    @Inject
    protected SharedBuffer sharedBuffer;
    // Inject from application.properties all parameters derived from default/environment variables/pod annotations(the latter overrides all the formers)
    @ConfigProperty(name = "general.interceptor.address")
    String interceptorAddress;
    @ConfigProperty(name = "general.interceptor.tokenAddress")
    String tokenAddress;

    @ConfigProperty(name = "general.interceptor.snapshotDate")
    String snapshotDate;
    @ConfigProperty(name = "general.interceptor.restrictedText")
    String restrictedText;
    @ConfigProperty(name = "general.interceptor.manifestName")
    String manifestName;
    @ConfigProperty(name = "general.interceptor.protectNullValues")
    String protectNullValues;
    @ConfigProperty(name = "general.interceptor.preserveStringLength")
    String preserveStringLength;
    @ConfigProperty(name = "general.interceptor.apiKey")
    protected String apiKey;
    @ConfigProperty(name = "general.interceptor.grantType")
    String grantType;
    @ConfigProperty(name = "general.interceptor.clientId")
    String clientId;
    @ConfigProperty(name = "general.interceptor.clientSecret")
    protected String clientSecret;
    @ConfigProperty(name = "general.interceptor.dataSetType")
    String dataSetType;
    @ConfigProperty(name = "general.interceptor.jobType")
    String jobType;


    private static final Client client = ClientBuilder.newClient();

    // this method being called just before the POST/GET method of the controller is being called 
    // and after the client invoke the HTTP Request.
    @ServerRequestFilter
    public void getRequestPath(ContainerRequestContext requestContext) {
        String requestBody = "";
        String headers = "";
        try {
            requestBody = new String(requestContext.getEntityStream().readAllBytes());
            headers = om.writerWithDefaultPrettyPrinter().writeValueAsString(requestContext.getHeaders());
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.infof("Received a Request, \n Headers= %s , \n url = %s  , \n body = %s \n", headers, requestContext.getUriInfo().getRequestUri().toString(), requestBody);
        Map bodyMap = new HashMap();
        try {
            bodyMap = om.readValue(requestBody, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // save the body of request to a shared buffer structure,  in order to propagate part of the body to the invocation of the interceptor on the response path.
        sharedBuffer.getMapOfKeys().put(Span.current().getSpanContext().getTraceId(), bodyMap);
        ;
        // add 3 headers to be used by GET and POST controllers methods in order to callthe actual microservice/application endpoint. 
        requestContext.getHeaders().put("x-route-to", List.of(requestContext.getUriInfo().getPath()));
        requestContext.getHeaders().put("x-host-name", List.of(requestContext.getUriInfo().getRequestUri().getHost()));
        requestContext.getHeaders().put("x-port-no", List.of(Integer.valueOf(requestContext.getUriInfo().getRequestUri().getPort()).toString()));
    }

    // this is called right after the POST/GET controllers methods are being returned, and when finished
    //   the manipulations of payload are returned to the client.   
    @ServerResponseFilter
    public void getResponsePath(ContainerResponseContext responseContext) {
        logger.info("in getResponsePath");
        String responseBodyMS = responseContext.getEntity().toString();
        //get token from interceptor's token endpoint.
        String token = getTokenForInvocation();
        logger.infof("Retrieved Token=%s", token);
        //invoke interceptor and passing to it the token for authentication, and response payload from microservice.
        MyResponseEntity theResponse = invokeInterceptor(token, responseBodyMS);
        logger.infof("Response from Interceptor Service=%s", theResponse);
        // override microservice response with interceptor' response, both body and headers.
        responseContext.setEntity(theResponse.getResponseBody());
        responseContext.getHeaders().clear();
        responseContext.getHeaders().putAll(theResponse.getHeaders());


    }
}
```
4. Create a ConfigSource implementation that will make the openshift annotations values to become a configuration source with greater precedence than environment variables, it's mapping the annotations using K8S Downward API volume mounted as volume in the container of the custom proxy interceptor App:
```java
@StaticInitSafe
public class OpenshiftAnnotationsConfigSource implements ConfigSource {
    public final static int annotationsConfigSourceOrdinal = 280;

    private static final Logger logger = Logger.getLogger("OpenshiftAnnotationsConfigSource.java");
    private static final Map<String, String> annotations = new HashMap<>();

    static {
//        String interceptorMode = ConfigProvider.getConfig().getValue("general.interceptor.mode",String.class);
//        Default running mode for proxy is ambassador container
        String interceptorMode = System.getenv("INTERCEPTOR_MODE") == null ? "ambassador" : System.getenv("INTERCEPTOR_MODE");
        if (interceptorMode.trim().equalsIgnoreCase("ambassador")) {
            logger.info("Proxy Interceptor is running in ambassador container mode, overriding configuration and environment variables from pod' Annotations (for all config that exists)");
            String annotationsPath = System.getenv("ANNOTATIONS_PATH");
            if (annotationsPath == null)
                annotationsPath = "/tmp/annotations/..data/annotations";

            loadAnnotationToConfig(annotationsPath);
        } else {
            logger.info("Proxy Interceptor is running in standalone mode, Configuration is overridden from environment variables only.");
        }
    }
}
```
#### Ambassador Mode
**Note: In this Mode, The proxy application assuming that per pod,  there is only one pod' container port exposed through k8s service, and this is the microservice' serving port.** 
1. create new project test-ambassador
```shell
oc new-project test-ambassador
```

2. If not already installed on cluster, [Install RH Service mesh Using the operator](../rhsm-istio-operator/servicemesh-operator/README.md), with test-ambassador project inside the ServiceMesh MemberRoll Instance.


3. Deploy Istio' `SideCar` custom resource to override default envoy proxy behavior inherited from mesh control plane, It will forward all ingress traffic coming to service on port 9999 to port 10000 on the loopback localhost network interface ( which in case of a POD is a network common to all containers), that way it will pass all traffic of pod to the proxy interceptor container:
```yaml
apiVersion: networking.istio.io/v1alpha3
kind: Sidecar
metadata:
  name: employees
spec:
  workloadSelector:
    labels:
      app: employees
      # Alternatively , you can defined a label like
      # intercept-by-proxy: true
      # On Each microservice pod that you want to intercept, in order to intercept a group of microservices.      
  ingress:
    - port:
        number: 9999
        protocol: HTTPS
        name: http
        # This is the port of the proxy interceptor running as ambassador container in the pod
      defaultEndpoint: 127.0.0.1:10000
```

```shell
oc apply -f ../../mocks/with-ambassador/sidecar.yaml
```

4. Create virtual service to define routing for the mocked service
```shell
oc apply -f ../../mocks/with-ambassador/virtual-service.yaml
```

5. Create a Gateway Instance, So We'll access the service through ingress gateway route from outside the cluster:
```shell
oc apply -f ../../mocks/with-ambassador/ingress-gateway.yaml
```
6. Deploy the mocked Application Deployment and inject to it the ambassador container using `kustomize`, it will also automate steps 3-5:
```shell
kustomize build ../../mocks/with-ambassador | oc apply -f -
```
7. Wait a few seconds and run the microservice:
```shell
curl -i --location --request POST 'http://istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com/employees' --header 'Content-Type: application/json' -d '{"countryCode": "GB", "dataOwningCountryCode": "IT"}'
```
Output:
```shell
HTTP/1.1 200 OK
matched-stub-id: 89052c3b-6c50-4818-ad8a-c4f90a7d9563
vary: Accept-Encoding, User-Agent
x-quota-limit: 10000
x-quota-remaining: 9666
server: istio-envoy
date: Thu, 23 Mar 2023 15:59:57 GMT
x-gravitee-request-id: a2571aff-7724-4610-971a-ff772416103d
x-rate-limit-limit: 100
x-rate-limit-remaining: 98
content-type: application/json
content-length: 733
x-envoy-upstream-service-time: 1482
x-rate-limit-reset: 1679587256800
x-gravitee-transaction-id: a2571aff-7724-4610-971a-ff772416103d
x-content-type-options: nosniff
x-quota-reset: 1681642457131
x-frame-options: SAMEORIGIN
set-cookie: cd10b69e39387eb7ec9ac241201ab1ab=7707cb491b328913d50465deab41c3fb; path=/; HttpOnly

{"dataSet":"{\n  \"employees\": {\n    \"employee\": [\n      {\n        \"id\": \"1\",\n        \"firstName\": \"*********\",\n        \"lastName\": \"*********\",\n        \"fullName\": \"Robert Brownforest\",\n        \"DOB\": \"01/01/0001\",\n        \"email\": \"RB1@exate.com\",\n        \"photo\": \"https://pbs.twimg.com/profile_images/735509975649378305/B81JwLT7.jpg\"\n      },\n      {\n        \"id\": \"2\",\n        \"firstName\": \"*********\",\n        \"lastName\": \"*********\",\n        \"fullName\": \"Rip Van Winkle\",\n        \"DOB\": \"01/01/0001\",\n        \"email\": \"RVW1@exate.com\",\n        \"photo\": \"https://pbs.twimg.com/profile_images/735509975649378305/B81JwLT7.jpg\"\n      }\n    ]\n  }\n}"}
```
8.  Open Kiali Dashboard , and if needed, authenticate using Openshift Credentials:
```shell
xdg-open http://$(oc get route kiali -n istio-system -o=jsonpath="{..spec.host}")
```
Note: You can Enter the following command and copy+paste it into browser in order to enter it manually in the URL bar:
```shell
oc get route kiali -n istio-system -o=jsonpath="{..spec.host}"
```

**Note: For the following command , you will need a client-rest-api pod, here is the command to deploy ir in this namespace:**
```shell
oc apply -f ../../rest-client-pod-sidecar.yaml -n test-ambassador
```

9. Invoke 2 invocations to service, 1 via ingress gateway , and 1 from inside the mesh , 50 times in loop
```shell

export INGRESS_HOST=http://istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com
export HOST=http://employees-api.test-ambassador:9999
for i in {1..50}
do 
oc exec rest-api-client -- curl --location --request POST ''$HOST'/employees' --header 'Content-Type: application/json'  --data-raw '{"countryCode": "GB", "dataOwningCountryCode": "GB"}'
sleep 1
echo
echo
curl --location --request POST ''$INGRESS_HOST'/employees' --header 'Content-Type: application/json'  --data-raw '{"countryCode": "GB", "dataOwningCountryCode": "GB"}'
echo  
echo
done
```

10. Check the kiali dashboard for visualization of how the traffic flows.


11. When finished, uninstall everything:
```shell
kustomize build ../../mocks/with-ambassador | oc delete -f -
oc delete -f ../../rest-client-pod-sidecar.yaml -n test-ambassador --grace-period=0 --force
```

#### Standalone Mode

1. If needed, Repeat Step 2 from [Ambassador Mode Procedure](#ambassador-Mode) and switch to/create project test-sa
```shell
oc project test-sa
```

2. Deploy Ingress Gateway to define ingress endpoint for the microservice in the mesh:
```yaml
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: demo-ingress
spec:
  selector:
    istio: ingressgateway
  servers:
    - port:
        number: 80
        name: http
        protocol: HTTP
      hosts:
        - "*"
```
```shell
oc apply -f ../../mocks/with-proxy-interceptor-standalone/ingress-gateway.yaml
```
3. Create virtual Service to define all routes, from outside cluster, from within mesh, and from proxy interceptor, to the microservice service( need to be created as per microservice' service):
```yaml
kind: VirtualService
apiVersion: networking.istio.io/v1alpha3
metadata:
  name: ingress-gateway-to-employees
  labels:
    policyResource: "true"
spec:
  hosts:
#  - "*.test-sa.svc.cluster.local"
  - employees-api.test-sa.svc.cluster.local
  - istio-ingressgateway.istio.system.svc.cluster.local
  - istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com

  gateways:
    - demo-ingress
    - mesh
  http:
  - name: prevent-interception-with-designated-header
    match:
      - headers:
          bypass-interception:
            exact: "true"
    route:
      - destination:
          host: employees-api.test-sa.svc.cluster.local
          port:
            number: 9999     
  - name: route-from-proxy-interceptor-itself
    match:
    - sourceLabels:
        app: "proxy-interceptor"
    route:
        - destination:
            host: employees-api.test-sa.svc.cluster.local
            port:
              number: 9999
  - name: route-from-internal-clients
    match:
    - sourceLabels:
        intercepted-by-proxy: "true"
    route:
      - destination:
          host: proxy-interceptor.test-sa.svc.cluster.local
          port:
            number: 10000
        headers:
          request:
            add:
              x-source-origin: pod-within-mesh
  - name: route-from-outside-mesh-via-ingress-gateway
    match:
    - uri:
        prefix: "/employees"
    rewrite:
      authority: employees-api:9999
    route:
      - destination:
          host: proxy-interceptor.test-sa.svc.cluster.local
          port:
            number: 10000
        headers:
          request:
            add:
              x-source-origin: ingress-gateway
```

```shell
oc apply -f ../../mocks/with-proxy-interceptor-standalone/virtual-service.yaml
```
4. Deploy Employees microservice and proxy interceptor deployments ( it will also automate steps 2-3 and namespace creation as well):
```shell
kustomize build ../../mocks/with-proxy-interceptor-standalone | oc apply -f -
```
5. Wait a few seconds and run the microservice:
```shell
curl -i --location --request POST 'http://istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com/employees' --header 'Content-Type: application/json' -d '{"countryCode": "GB", "dataOwningCountryCode": "IT"}'
```
Output:
```shell
HTTP/1.1 200 OK
matched-stub-id: 8905241b-6c50-4478-a6a4-a79a0c7d9178
vary: Accept-Encoding, User-Agent
x-quota-limit: 10000
x-quota-remaining: 9666
server: istio-envoy
date: Thu, 16 Apr 2023 16:30:40 GMT
x-gravitee-request-id: a2571aff-7724-4610-971a-ff772416103d
x-rate-limit-limit: 100
x-rate-limit-remaining: 98
content-type: application/json
content-length: 733
x-envoy-upstream-service-time: 1482
x-rate-limit-reset: 1679587256800
x-gravitee-transaction-id: a2571aff-7724-4610-971a-ff772416103d
x-content-type-options: nosniff
x-quota-reset: 1681642457131
x-frame-options: SAMEORIGIN
set-cookie: cd10b69e39387eb7ec9ac241201ab1ab=7707cb491b328913d50465deab41c3fb; path=/; HttpOnly

{"dataSet":"{\n  \"employees\": {\n    \"employee\": [\n      {\n        \"id\": \"1\",\n        \"firstName\": \"*********\",\n        \"lastName\": \"*********\",\n        \"fullName\": \"Robert Brownforest\",\n        \"DOB\": \"01/01/0001\",\n        \"email\": \"RB1@exate.com\",\n        \"photo\": \"https://pbs.twimg.com/profile_images/735509975649378305/B81JwLT7.jpg\"\n      },\n      {\n        \"id\": \"2\",\n        \"firstName\": \"*********\",\n        \"lastName\": \"*********\",\n        \"fullName\": \"Rip Van Winkle\",\n        \"DOB\": \"01/01/0001\",\n        \"email\": \"RVW1@exate.com\",\n        \"photo\": \"https://pbs.twimg.com/profile_images/735509975649378305/B81JwLT7.jpg\"\n      }\n    ]\n  }\n}"}
```
6. Open kiali, and if needed, authenticate using Openshift Credentials:
```shell
xdg-open http://$(oc get route kiali -n istio-system -o=jsonpath="{..spec.host}")
```
Note: You can Enter the following command and copy+paste it into browser in order to enter it manually in the URL bar:
```shell
oc get route kiali -n istio-system -o=jsonpath="{..spec.host}"
```

7. Execute 2 HTTP Requests 50 times in a loop, in order to see the traffic flows at kiali dashboard:
```shell
export HOST=http://istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com
for i in {1..50}
do 
curl --location --request POST ''$HOST'/employees' --header 'Content-Type: application/json'  --data-raw '{"countryCode": "IL", "dataOwningCountryCode": "IL"}'
sleep 1
echo
echo
curl --location --request POST ''$HOST'/employees' --header 'Content-Type: application/json' --header 'bypass-interception: true' --data-raw '{"countryCode": "IL", "dataOwningCountryCode": "IL"}'
echo  
echo
done
```

8. In parallel , on another terminal window, create a rest client pod and enter it:
```shell
oc apply -f ../../rest-client-pod-sidecar.yaml -n test-sa
oc wait --for=condition=Ready=true pod/rest-api-client
oc exec -it rest-api-client -- bash
```
9. From inside the client pod, execute 2 HTTP Requests 50 times in a loop, in order to see the traffic flows at kiali dashboard:
```shell
export HOST=http://employees-api.test-sa:9999
for i in {1..50}
do 
curl --location --request POST ''$HOST'/employees' --header 'Content-Type: application/json'  --data-raw '{"countryCode": "IL", "dataOwningCountryCode": "IL"}'
sleep 1
echo
echo
curl --location --request POST ''$HOST'/employees' --header 'Content-Type: application/json'  --header 'bypass-interception: true' --data-raw '{"countryCode": "IL", "dataOwningCountryCode": "IL"}'
echo  
echo
done
```
10. When finished, uninstall everything:
```shell
kustomize build ../../mocks/with-proxy-interceptor-standalone | oc delete -f -
oc delete -f ../../rest-client-pod-sidecar.yaml -n test-sa --grace-period=0 --force
```
