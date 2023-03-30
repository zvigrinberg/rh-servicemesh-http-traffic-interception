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

**Note: The proxy application assuming that per pod,  there is only one pod' container port exposed through k8s service, and this is the microservice' serving port.** 
#### Ambassador Mode
1. create new project test-ambassador
```shell
oc new-project test-ambassador
```

2. If not already installed on cluster, [Install RH Service mesh Using the operator](../rhsm-istio-operator/servicemesh-operator/README.md), with test-ambassador project inside the ServiceMesh MemberRoll Instance.


3. Deploy Istio' `SideCar` custom resource to override default envoy proxy behavior inherited from mesh control plane, It will forward all ingress traffic comming to service on port 9999 to port 10000 on the loopback localhost network interface ( which in case of a POD is a network common to all containers), that way it will pass all traffic of pod to the proxy interceptor container:
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
7. Wait a few seconds and run the microservice again:
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


8. When finished, uninstall everything:
```shell
kustomize build ../../mocks/with-ambassador | oc delete -f -
```

#### Standalone Mode

TBD