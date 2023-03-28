package com.redhat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.vertx.core.http.impl.Http1xServerRequest;
import io.vertx.core.http.impl.HttpServerRequestWrapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;


public class WebFilter {

    private final Logger logger = Logger.getLogger("WebFilter.java");
    private final ObjectMapper om = new ObjectMapper();
    @Inject
    private SharedBuffer sharedBuffer;
    @ConfigProperty( name = "general.interceptor.address")
    private String interceptorAddress;
    @ConfigProperty( name = "general.interceptor.tokenAddress")
    private String tokenAddress;

    @ConfigProperty( name = "general.interceptor.snapshotDate")
    private String snapshotDate;
    @ConfigProperty( name = "general.interceptor.restrictedText")
    private String restrictedText;
    @ConfigProperty( name = "general.interceptor.manifestName")
    private String manifestName;
    @ConfigProperty( name = "general.interceptor.protectNullValues")
    private String protectNullValues;
    @ConfigProperty( name = "general.interceptor.preserveStringLength")
    private String preserveStringLength;
    @ConfigProperty( name = "general.interceptor.apiKey")
    private String apiKey;
    @ConfigProperty( name = "general.interceptor.grantType")
    private String grantType;
    @ConfigProperty( name = "general.interceptor.clientId")
    private String clientId;
    @ConfigProperty( name = "general.interceptor.clientSecret")
    private String clientSecret;
    @ConfigProperty( name = "general.interceptor.dataSetType")
    private String dataSetType;
    @ConfigProperty( name = "general.interceptor.jobType")
    private String jobType;


    private static final Client client = ClientBuilder.newClient();
    @ServerRequestFilter
    public void getRequestPath(ContainerRequestContext requestContext) {
        logger.info("in getRequestPath");
        Map bodyMap = new HashMap();
        try {

            String requestBody = new String(requestContext.getEntityStream().readAllBytes());
            bodyMap = om.readValue(requestBody,Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        sharedBuffer.getMapOfKeys().put(Span.current().getSpanContext().getTraceId(),bodyMap);
          ;
          requestContext.getHeaders().put("x-route-to", List.of(requestContext.getUriInfo().getPath()));


        }
    @ServerResponseFilter
    public void getResponsePath(ContainerResponseContext responseContext) {
        logger.info("in getResponsePath");
        String responseBodyMS = responseContext.getEntity().toString();
        String token = getTokenForInvocation();
        String theResponse = invokeInterceptor(token,responseBodyMS);
        responseContext.setEntity(theResponse);

        }

    private String invokeInterceptor(String token, String responseBodyMS) {
       WebTarget interceptorServer = client.target(this.interceptorAddress);
        InterceptorModel theBody = populateInterceptorBody(responseBodyMS);
        String requestBody="";
        try {
            requestBody = om.writeValueAsString(theBody);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        Response finalBody = interceptorServer
                           .request()
                           .header("X-Api-Key",this.apiKey)
                           .header("X-Resource-Token", "Bearer " + token)
                           .header("X-Data-Set-Type",this.dataSetType)
                           .header("Content-Type",APPLICATION_JSON)
                           .post(Entity.entity(requestBody,APPLICATION_JSON));

      return finalBody.readEntity(String.class);
    }

    private InterceptorModel populateInterceptorBody(String responseBodyMS) {
        String adjustedResponseBody = responseBodyMS.replace("\"", "'");
        Map propagatedRequestBody = sharedBuffer.getMapOfKeys().get(Span.current().getSpanContext().getTraceId());
        String countryCode = propagatedRequestBody.get("countryCode") !=null ? (String) propagatedRequestBody.get("countryCode") : "GB";
        String dataOwningCountryCode = propagatedRequestBody.get("dataOwningCountryCode") != null ? (String) propagatedRequestBody.get("dataOwningCountryCode") : "GB";
        return InterceptorModel.builder()
                        .countryCode(countryCode)
                        .dataOwningCountryCode(dataOwningCountryCode)
                        .protectNullValues(Boolean.getBoolean(this.protectNullValues))
                        .preserveStringLength(Boolean.getBoolean(this.preserveStringLength))
                        .manifestName(this.manifestName)
                        .restrictedText(this.restrictedText)
                        .snapshotDate(this.snapshotDate)
                        .jobType(this.jobType)
                        .dataSet(adjustedResponseBody)
                        .build();
    }

    private String getTokenForInvocation() {
        WebTarget tokenServer = client.target(tokenAddress);
        Response bodyWithToken = tokenServer.request()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Api-Key", this.apiKey)
                .post(Entity.entity("client_id=" + this.clientId + "&client_secret=" + this.clientSecret + "&grant_type=" + this.grantType, APPLICATION_FORM_URLENCODED));
        Map bodyEntries = bodyWithToken.readEntity(Map.class);

        return (String)bodyEntries.get((String)"access_token");
    }

}
