# RH ServiceMesh HTTP Traffic-interception

## Goal
 To demonstrate several ways how to intercept http traffic using RH Service MESH without changing Clients' and servers' code.


### Prerequisites to Demos

- You need to install RH Service Mesh operator and install a control plane using it.
  Please go over the following manual : 
  [here](./istio/rhsm-istio-operator/servicemesh-operator/README.md)

### Demos

#### Tested Configuration
* All Demos were Tested with the following versions:
  1. Red Hat OpenShift Service Mesh version 2.3.2, which contains:
  2. Istio Version 1.14.5
  3. Envoy Proxy 1.22.7
#### List of Demos  
1. Traffic Interception Using [EnvoyFilter HTTP Lua Filter](istio/using-http-lua-filter/README.md)
2. Traffic Interception Using [Generic Interceptor Proxy Application](istio/using-ambassador-container-app/README.md):
   1. Ambassador Container Mode.
   2. Standalone Mode.
3. Traffic Interception Using [Wasm plugin](https://istio.io/latest/docs/reference/config/proxy_extensions/wasm-plugin/), [Here](./istio/using-wasm-plugin/README.md)

**Note: If one of the namespaces deleted from cluster and recreated, and servicemesh member roll CustomResource is out of sync ( Namespace is not returned to SM Member Roll or not shown on Kiali), you can use the replacement strategy to recreate the member roll CR:**
```shell
oc replace -f istio/rhsm-istio-operator/servicemesh-operator/servicemesh-member-roll.yaml --force
```

#### General Troubleshooting 
- In case you're getting error 503 ( Service Unavailable ) when trying to access through ingress gateway route, it means that you have duplicate gateway definitions that selecting the ingress gateway from istio control plane namespace at least in two namespaces, In order to diagnose run the following command:
    ```shell
     oc get gateway -A
    ```
    If you get more than one result, then this is the problem and you should delete the leftover gateway from the namespace which you're not currently working on, wait few seconds and you'll get again 200 status as expected.