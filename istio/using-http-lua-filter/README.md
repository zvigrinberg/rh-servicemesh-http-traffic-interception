# Lua Http Filter Using Istio EnvoyFilter

## Goal

To Intercept http  Traffic on the request and on the response paths without changing the applications of microservices or clients that are using them.

### Procedure

1. create new project test
```shell
oc new-project test
```

2. Install RH Service mesh Using the operator, with test project inside the ServiceMesh MemberRoll Instance.
3. Deploy Istio' `ProxyConfig` to set environment variables inside sidecar proxy of pod of the employees microservice.
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
oc apply -f proxy-config.yaml
```

4. Deploy a mocked microservice employees ( using Wiremock MOCK Server):
```shell
oc apply --kustomize=../../mocks/with-sidecar
```


4. First, Invoke the microservice without intercepting it with envoyfilter
```shell
curl -i --location --request POST 'http://istio-ingressgateway-istio-system.apps.exate-us-west.fsi.rhecoeng.com/employees' --header 'Content-Type: application/json' -d '{"countryCode": "GB", "dataOwningCountryCode": "IT"}'
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

5. Create the EnvoyFilter on the cluster
```shell
oc apply -f http-lua-envoy-filter.yaml
```

6. Create virtual service to define routing for the mocked service
```shell
oc apply -f ../virtual-service.yaml
```

7. Create a Gateway Instance, So We'll access the service through ingress gateway route from outside the cluster:
```shell
oc apply -f ../ingress-gateway.yaml
```

8. Wait a few seconds and run the microservice again:
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