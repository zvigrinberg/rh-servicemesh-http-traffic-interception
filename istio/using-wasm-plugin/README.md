# Traffic Interception Using proxy-wasm Module plugin


## Introduction

### WASM

WASM ( Abbreviation of WebAssembly) is a binary instruction format for a stack-based virtual machine. Wasm is designed as a portable compilation target for programming languages, enabling deployment on the web for client and server applications. \
WASM Binary Module can run on the browser or on the server side inside a sandbox Virtual machine, completely isolated from its host server.


### Proxy-WASM - WebAssembly for Proxies

[WebAssembly for Proxies (ABI specification)](https://github.com/proxy-wasm/spec) - is a low level ABI (Application Binary Interface) and sets of rules and conventions  to be used in Layer 4/7 Proxies Servers.
It's proxy agnostic, as long as the proxy implement this interface, but was originally designed and tailored for envoy proxy ( which istio-proxy is based on it).
This way Envoy, Istio, and other implementing ("host environment") proxies , can extend their behaviors using proxy-wasm modules using extensions.

In Fact, Envoy Proxy is invoking methods/functions defined on the WASM Plugin Module, as per the Interface, According to the lifecycle of the HTTP Request ( In Envoy, It's according to the lifecycle of the HTTP Request defined and controlled by [HTTP Connection manager](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/http/http_connection_management))


Basically, WASM is supported in more than 20 languages (correct for now), as you can see in the following [link](https://www.fermyon.com/wasm-languages/webassembly-language-support), some programming language support it fully or partially, or through variant of the official compiler, but in order to write proxy-wasm modules, it's better to use proxy-wasm SDK, correct for today, these are the 4 SDK for proxy-wasm:

1. [AssemblyScript SDK](https://github.com/solo-io/proxy-runtime) - TypeScript/TypeScript variant for WASM.
2. [C++ SDK](https://github.com/proxy-wasm/proxy-wasm-cpp-sdk)
3. [Rust SDK](https://github.com/proxy-wasm/proxy-wasm-rust-sdk)
4. [Go (TinyGo) SDK](https://github.com/tetratelabs/proxy-wasm-go-sdk)


They are ordered by descending order of maturity and robustness ( 1 being the most mature and 4 the less one), the maturity also impacted by the maturity of Integration of WASM/WASI with the SDKs' Languages. 

It's more recommended to use SDK rather than implement the WASM-Proxy ABI Yourself  ,which is low-level implementation ,for your language of choice ( that supports WASM/WASI , off course).




## Goal

To Build A Proxy-WASM module extension for RH Service Mesh in order to Demonstrate interception and manipulation of HTTP Traffic.

## Prerequisites

1. Need to choose SDK for writing the WASM-proxy Plugin.
2. Need to implement a sample proxy-wasm plugin, and compile it to a Wasm Binary format.
   For this demonstration, we'll use the following [demo wasm plugin](https://github.com/zvigrinberg/wasm-proxy-go-demo), which i developed using the GO Wasm-Proxy SDK - need to follow instructions there to compile the source code to WASI target and containerize it.
3. Need latest version of kustomize - [get it here](https://kubectl.docs.kubernetes.io/installation/kustomize/binaries/)

## Procedure

1. Create new namespace for demo ( if not already exists)
```shell
oc new-project test-wasm
```
2. Create Ingress Gateway to define ingress endpoint for the mesh
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

3. Create Virtual Service to create the route to employees-api mocked microservice from the ingress gateway and from each proxy in the mesh
```yaml
kind: VirtualService
apiVersion: networking.istio.io/v1alpha3
metadata:
  name: employees-api
  labels:
    policyResource: "true"
spec:
  hosts:
    - istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com
    - employees-api.test.svc.cluster.local
  gateways:
    - demo-ingress
    - mesh
  http:
  - match:
    - uri:
        exact: "/employees"
    route:
    - destination:
        host: employees-api.test.svc.cluster.local
        port:
          number: 9999
```

4. Create ProxyConfig Custom Resource to define environment variables for side-car proxy of the employees-api microservice pod
```yaml
apiVersion: networking.istio.io/v1beta1
kind: ProxyConfig
metadata:
  name: employees-proxy-config
spec:
  selector:
    matchLabels:
      app: employees
  concurrency: 0
  environmentVariables:
    MANIFEST_NAME: "Employee"
    JOB_TYPE: "Restrict"
    PROTECT_NULL_VALUES: "true"
    PRESERVE_STRING_LENGTH: "false"
    SNAPSHOT_DATE: "2023-03-20T00:00:00Z"
    RESTRICTED_TEXT: "*********"
    API_KEY: changeme
    CLIENT_ID: postman
    CLIENT_SECRET: changeme
```
5. Define External Interceptor as External Service Entry for the mesh
```yaml
apiVersion: networking.istio.io/v1alpha3
kind: ServiceEntry
metadata:
  name: apigator-svc
spec:
  hosts:
    - exate.co
  location: MESH_EXTERNAL
  resolution: DNS
  ports:
  - number: 443
    name: https
    protocol: TLS
  endpoints:
  - address:  api.exate.co
```

6. Define the following destination rule upon the external interceptor in order to let clients within the mesh invoke the interceptor using TLS origination
```yaml
# This Will make sure there will be TLS origination when client call this external service
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name:  originate-tls-apigator-svc
spec:
  host: exate.co
  trafficPolicy:
    tls:
      mode: SIMPLE
```

7. Define the proxy-wasm plugin Custom resource in order to extend istio-proxy with the Wasm Plugin we developed, specify the container registry + image, and specify the list of env variables to be exposed by side-car proxy to the WASM Module VM
```yaml
apiVersion: extensions.istio.io/v1alpha1
kind: WasmPlugin
metadata:
  name: apigator-wasm-plugin
spec:
  selector:
    matchLabels:
      intercept-payload: "true"
  url: oci://quay.io/zgrinber/golang-wasm-plugin:latest
  imagePullPolicy: IfNotPresent
#  imagePullSecret: my-pull-secret
  pluginConfig:
    value: test
  vmConfig:
    env:
      - name: JOB_TYPE
        valueFrom: HOST

      - name: CLIENT_ID
        valueFrom: HOST

      - name: CLIENT_SECRET
        valueFrom: HOST

      - name: API_KEY
        valueFrom: HOST

      - name: RESTRICTED_TEXT
        valueFrom: HOST

      - name: MANIFEST_NAME
        valueFrom: HOST

      - name: PROTECT_NULL_VALUES
        valueFrom: HOST

      - name: PRESERVE_STRING_LENGTH
        valueFrom: HOST
# Envoy proxy Cluster name.
      - name: INTERCEPTOR_CLUSTER_NAME
        value: outbound|443||exate.co


```

8. Deploy the mocked employees microservice by applying the following manifest to the cluster:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: employees-api

spec:
  selector:
    matchLabels:
      app: employees
  template:
    metadata:
      labels:
        app: employees
        intercept-payload: "true"
      annotations:
        sidecar.istio.io/inject: 'true'
        "sidecar.istio.io/agentLogLevel": debug
        # Increase log level of istio-proxy to be debug, in order to debug and troubleshoot.
        "sidecar.istio.io/logLevel": debug
    spec:
      volumes:
        - name: shared-volume
          emptyDir: {}
        - name: mocks-file
          configMap:
            name: json-mappings

      containers:
        - name: wiremock-server
          image: "quay.io/zgrinber/wiremock:latest"
          command: [ "bash" , "-c" ,"java -jar /var/wiremock/lib/wiremock-jre8-standalone.jar --port 9999"]
          volumeMounts:
            - mountPath: /tmp/mocks
              name: mocks-file
          ports:
            - containerPort: 9999
              name: http
          lifecycle:
            postStart:
              exec:
                command: [ "/bin/sh","-c", " sleep 1 ; curl -X POST http://localhost:9999/__admin/mappings/import -T /tmp/mocks/mappings.json " ]
```

9. Automate the whole process of section 2-8 using the following command
```shell
kustomize build ../../mocks/with-wasm-proxy/ | oc apply -f -
```

10. Open Kiali Dashboard In your default web browser.
```shell
xdg-open http://$(oc get route kiali -n istio-system -o=jsonpath="{..spec.host}")

```

11. Deploy a rest api client pod in the mesh
```shell
oc apply -f ../../rest-client-pod-sidecar.yaml -n test-wasm
```

12. Run 50 REST API Calls to Microservice
```shell
 for i in {1..50}; do echo; oc exec -it rest-api-client -- curl  -i -X POST http://employees-api.test-wasm.svc.cluster.local:9999/employees -d '{"countryCode": "GB", "dataOwningCountryCode": "GB" }'  --header 'Content-Type: application/json' --header 'Accept: application/json'; echo; done
```

13. Check the kiali dashboard for how traffic flows in the mesh, and see statistics and logs.


14. At the end - Delete Everything
```shell
kustomize build ../../mocks/with-wasm-proxy/ | oc delete -f -
oc delete -f ../../rest-client-pod-sidecar.yaml -n test-wasm --grace-period=0 --force
```
