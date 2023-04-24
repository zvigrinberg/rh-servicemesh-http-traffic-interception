# Lua Http Filter Using Istio EnvoyFilter

## Goal

To Intercept http  Traffic on the request and on the response paths without changing the applications of microservices or clients that are using them.

### Procedure

1. create new project test
```shell
oc new-project test
```
or switch to test project
```shell
oc project test
```

2. [Install RH Service mesh Using the operator](../rhsm-istio-operator/servicemesh-operator/README.md), with test project inside the ServiceMesh MemberRoll Instance.
3. Deploy a mocked microservice employees ( using Wiremock MOCK Server):
```shell
oc apply --kustomize=../../mocks/deploy-microservice-alone
```

4. First, Invoke the microservice without intercepting it with envoyfilter
```shell
curl --location --request POST 'http://istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com/employees' --header 'Content-Type: application/json' -d '{"countryCode": "GB", "dataOwningCountryCode": "IT"}' | jq .
sleep 2
oc delete --kustomize=../../mocks/deploy-microservice-alone 
```
Output:
```shell
HTTP/1.1 200 OK
matched-stub-id: 89052c3b-6c50-4818-ad8a-c4f90a7d9563
..............

{
    "employees": {
        "employee": [
            {
                "id": "1",
                "firstName": "Robert",
                "lastName": "Brownforest",
                "fullName": "Robert Brownforest",
                "DOB": "18/12/1965",
                "email": "RB1@exate.com",
                "photo": "https://pbs.twimg.com/profile_images/735509975649378305/B81JwLT7.jpg"
            },
            {
                "id": "2",
                "firstName": "Rip",
                "lastName": "Van Winkle",
                "fullName": "Rip Van Winkle",
                "DOB": "18/01/1972",
                "email": "RVW1@exate.com",
                "photo": "https://pbs.twimg.com/profile_images/735509975649378305/B81JwLT7.jpg"
            }
        ]
    }
}
```

5. Deploy Istio' `ProxyConfig` to set environment variables inside sidecar proxy of pod of the employees microservice.
```yaml
apiVersion: networking.istio.io/v1beta1
kind: ProxyConfig
metadata:
  name: employees-proxy-config
  namespace: test
spec:
  selector:
    matchLabels:
      app: employees
  concurrency: 0
  environmentVariables:
#    apiGatorAddress: https://api.exate.co:443/apigator/protect/v1/dataset
    manifestName: "Employee"
    jobType: "Restrict"
    protectNullValues: "true"
    preserveStringLength: "false"
    snapshotDate: "2023-03-20T00:00:00Z"
    restrictedText: "*********"
    dataSetType: JSON
    apiKey: ChangeMe
    clientId: postman
    grantType: client_credentials
    clientSecret: ChangeMe



```
```shell
oc apply -f ../../mocks/with-lua-http-filter-unsorted/proxy-config.yaml
```


6. Create the EnvoyFilter on the cluster (Pay attention to the `Cluster` Definition - Starting with `applyTo: CLUSTER`, it defined an upstream HTTPS Connection to apiGator Service to be available from the lua code )
```yaml
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: employees-http-lua-filter
  namespace: test
spec:
  workloadSelector:
    labels:
      app: employees
  configPatches:
    # The first patch adds the lua filter to the listener/http connection manager
    - applyTo: HTTP_FILTER
      match:
        context: SIDECAR_INBOUND
        listener:
          portNumber: 9999
          filterChain:
            filter:
              name: "envoy.filters.network.http_connection_manager"
              subFilter:
                name: "envoy.filters.http.router"
      patch:
        operation: INSERT_BEFORE
        value: # lua filter specification
          name: envoy.filters.http.lua
          typed_config:
            "@type": "type.googleapis.com/envoy.extensions.filters.http.lua.v3.Lua"
            inlineCode: |
              function envoy_on_request(request_handle)
                request_handle:logInfo("Start of envoy_on_request")
                package.path = package.path .. ";/lua/libraries/json.lua"
                json = require "json"
                local reqBody = request_handle:body()
                local stringReqBody = tostring(reqBody:getBytes(0, reqBody:length()))
                local requestTable = json.decode(stringReqBody)
                print(string.format("envoy_on_request - countryCode = %s, dataOwningCountryCode= %s  ", requestTable["countryCode"],requestTable["dataOwningCountryCode"]))
                request_handle:streamInfo():dynamicMetadata():set("envoy.filters.http.lua","countryCode",requestTable["countryCode"])
                request_handle:streamInfo():dynamicMetadata():set("envoy.filters.http.lua","dataOwningCountryCode",requestTable["dataOwningCountryCode"])
                local client_id = os.getenv("clientId")
                local client_secret = os.getenv("clientSecret")
                local grant_type = os.getenv("grantType")
                local urlEncodedBody = "client_id=" .. client_id .. "&client_secret=" .. client_secret .. "&grant_type=" .. grant_type
                print(string.format("envoy_on_request - urlEncodedBody = %s ", urlEncodedBody))
                local headers, bodyString = request_handle:httpCall(
                "api_gator_api",
                {
                  [ ":method" ] = "POST",
                  [ ":path" ] = "/apigator/identity/v1/token",
                  [ ":authority" ] = "api.exate.co",
                  [ "X-Api-Key" ] = os.getenv("apiKey"),
                  [ "Content-Type" ] = "application/x-www-form-urlencoded"
                },
                urlEncodedBody,
                5000)
                local tokenTable = json.decode(bodyString)
                print(string.format("envoy_on_request - token fetched = %s ", tokenTable["access_token"]))
                request_handle:streamInfo():dynamicMetadata():set("envoy.filters.http.lua","access_token",tokenTable["access_token"])
                request_handle:logInfo("End of envoy_on_request")
              end

              function envoy_on_response(response_handle)
                response_handle:logInfo("Start of envoy_on_response")
                print(string.format("Start of envoy_on_response"))
                package.path = package.path .. ";/lua/libraries/json.lua"
                json = require "json"
                local t = {}


                t["dataOwningCountryCode"] = response_handle:streamInfo():dynamicMetadata():get("envoy.filters.http.lua")["dataOwningCountryCode"]
                t["countryCode"] = response_handle:streamInfo():dynamicMetadata():get("envoy.filters.http.lua")["countryCode"]
                t["jobType"] = os.getenv("jobType")
                t["protectNullValues"] = true
                t["preserveStringLength"] = false
                t["manifestName"] = os.getenv("manifestName")
                t["restrictedText"] = os.getenv("restrictedText")

                local bodyFromMs = response_handle:body()
                local bodyMs = tostring(bodyFromMs:getBytes(0, bodyFromMs:length()))
                local bodyMsQuotes = string.gsub(bodyMs, "\"", "'")
                t["dataSet"] = bodyMsQuotes

                t["snapshotDate"] = os.getenv("snapshotDate")
                local apiGatorBody = json.encode(t)
                print(string.format("apiGatorBody= %s",apiGatorBody))
                response_handle:logInfo("Body request to apiGator rendered: "..apiGatorBody)
                -- Make an HTTP call to an upstream host with the following headers, body, and timeout.
                print(string.format("Checkpoint debug 1"))
                print(string.format("value of token = %s" , response_handle:streamInfo():dynamicMetadata():get("envoy.filters.http.lua")["access_token"]))
                local headers2, bodyString = response_handle:httpCall(
                 "api_gator_api",
                 {
                  [":method"] = "POST",
                  [":path"] = "/apigator/protect/v1/dataset",
                  [":authority"] = "api.exate.co",
                  ["X-Api-Key"] = os.getenv("apiKey"),
                  ["X-Data-Set-Type"] = os.getenv("dataSetType"),
                  ["X-Resource-Token"] = "Bearer" .. " " .. response_handle:streamInfo():dynamicMetadata():get("envoy.filters.http.lua")["access_token"],
                  ["Content-Type"] = "application/json"
                 },
                apiGatorBody,
                5000)
                print(string.format("Checkpoint debug 2"))
                print(string.format("bodyString= %s", bodyString))
                for key, value in pairs(headers2) do
                   print(string.format("key %s= %s",key,value))
                end
                print("")
                response_handle:logInfo("Body Returned: ".. bodyString)
                response_handle:logInfo("End of envoy_on_response")
                response_handle:body():setBytes(bodyString)
                local originalHeaders = response_handle:headers()

                for key, value in pairs(headers2) do
                    response_handle:headers():replace(key,value)
                end
              end
    # The second patch adds the cluster that is referenced by the lua code
    # cds match is omitted as a new cluster is being added
    - applyTo: CLUSTER
      match:
        context: SIDECAR_OUTBOUND
      patch:
        operation: ADD
        value: # cluster specification
          name: "api_gator_api"
          transport_socket:
            name: envoy.transport_sockets.tls
            typed_config:
              "@type": type.googleapis.com/envoy.extensions.transport_sockets.tls.v3.UpstreamTlsContext
          type: STRICT_DNS
          connect_timeout: 0.5s
          lb_policy: ROUND_ROBIN
          load_assignment:
            cluster_name: api_gator_api
            endpoints:
              - lb_endpoints:
                  - endpoint:
                      address:
                        socket_address:
                          protocol: TCP
                          address: "api.exate.co"
                          port_value: 443

```
```shell
oc apply -f ../../mocks/with-lua-http-filter-unsorted/http-lua-envoy-filter.yaml
```

7. Create virtual service to define routing for the mocked service
```shell
oc apply -f ../../mocks/with-lua-http-filter-unsorted/virtual-service.yaml
```

8. Create a Gateway Instance, So We'll access the service through ingress gateway route from outside the cluster:
```shell
oc apply -f ../../mocks/with-lua-http-filter-unsorted/ingress-gateway.yaml
```

9. Wait a few seconds and run the microservice again:
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

10. Steps 5-8 can be automated using a `kustomization` overlay, if you have `kustomize` version >= 5.0.0,
    Then run the following to automate the whole process.
```shell
kustomize build ../../mocks/with-lua-http-filter-sorted | oc apply -f -
```
If you have `kustomize` version < 5.0.0, then run the following Three commands:
```shell
kustomize build ../../mocks/with-lua-http-filter-unsorted | oc apply -f -
oc wait --for=condition=Available=true deployment/employees-api
oc rollout restart deployment employees-api
```

11. Open Kiali Dashboard , and if needed, authenticate using Openshift Credentials:
```shell
xdg-open http://$(oc get route kiali -n istio-system -o=jsonpath="{..spec.host}")
```
Note: You can Enter the following command and copy+paste it into browser in order to enter it manually in the URL bar:
```shell
oc get route kiali -n istio-system -o=jsonpath="{..spec.host}"
```

12. Deploy rest api client pod in order to be able to invoke the service also from within the mesh
```shell
 oc apply -f ../../rest-client-pod-sidecar.yaml -n test 
```

13. run 2 invocations of service, 1 via ingress gateway , and 1 from inside the mesh , 50 times in loop
```shell
export INGRESS_HOST=http://istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com
export HOST=http://employees-api.test:9999
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

14. Check the kiali dashboard for visualization of how the traffic flows.


15. When finished, uninstall everything:
```shell
kustomize build ../../mocks/with-lua-http-filter-unsorted | oc delete -f -
oc delete -f ../../rest-client-pod-sidecar.yaml -n test --grace-period=0 --force  
```