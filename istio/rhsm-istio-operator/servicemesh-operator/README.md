# Installing RH Service Mesh

## Goal
This guide will instruct how to deploy RH Service Mesh On an Openshift Cluster.

## Procedure

1. First Install Distributed Tracing Operator on Cluster (Jaeger):
```shell
oc apply -f jaeger-subscription-operatorgroup.yaml
oc apply -f jaeger-subscription.yaml
```

2. Install Kiali Operator on Cluster
```shell
oc apply -f kiali-subscription.yaml
```
3. Install Service Mesh Operator
```shell
oc apply -f rhsm-subscription.yaml
```

4. Watch CSV status on current namespace (Operator is targeting all namespaces) until PHASE= Succeeded
```shell
oc get csv | grep -E 'servicemesh|NAME'
```

5. create `project` istio-system
```shell
oc new-project istio-system
```

6. Install A RH Service Mesh Control Plane Using SMCP CR:
```shell
oc apply -f servicemesh-control-plane.yaml
```

7. Add 2 namespaces to the mesh , to be managed by control plane
```shell
oc apply -f servicemesh-member-roll.yaml
```
8. Wait until all components are ready
```shell
 oc get smcp -n istio-system
```
Expected Output:
```shell
NAME    READY   STATUS            PROFILES      VERSION   AGE
basic   9/9     ComponentsReady   ["default"]   2.3.1     20h
```