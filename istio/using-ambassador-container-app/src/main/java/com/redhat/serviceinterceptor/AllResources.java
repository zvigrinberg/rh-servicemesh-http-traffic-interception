package com.redhat.serviceinterceptor;

import com.oracle.svm.core.annotate.Inject;

import io.opentelemetry.context.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@ApplicationScoped
@AllArgsConstructor
@Data
@Path("")
public class AllResources {


    private Context context= Context.current();
    private final Logger logger = Logger.getLogger("AllResources.java");
    @Inject
    @Named("InvokeMicroserviceImpl")
    protected InvokeMicroservice invokeMicroservice;
    public AllResources() {
    }
    @ConfigProperty( name = "general.interceptor.mode")
    String interceptorMode;

    @ConfigProperty( name = "general.interceptor.servicePort")
    String servicePort;
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{s:.*}")
    public Response handleGet(@HeaderParam("x-route-to")  String routeTo) {
        logger.info("got a get Response");
        ServiceParametersEntity serviceParametersEntity = setServiceParameters();

        MyResponseEntity response = invokeMicroservice.run(routeTo, serviceParametersEntity.getFinalPort(), serviceParametersEntity.getTransportAndHost());
        return Response.ok(response.getResponseBody()).replaceAll(response.getHeaders()).build();

    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{s:.*}")
    public Response handlePost(@HeaderParam("x-route-to") String routeTo) {
        logger.info("got a POST Response");
        ServiceParametersEntity serviceParametersEntity = setServiceParameters();
        MyResponseEntity response = invokeMicroservice.run(routeTo, serviceParametersEntity.getFinalPort(), serviceParametersEntity.getTransportAndHost());
        return Response.ok(response.getResponseBody()).replaceAll(response.getHeaders()).build();
    }

    private ServiceParametersEntity setServiceParameters() {
        String finalPort;
        String transportAndHost;
        if(interceptorMode.equalsIgnoreCase("ambassador"))
        {
            transportAndHost = "http://localhost:";
            finalPort = InterceptorsUtils.findServicePort();
            if (finalPort.trim().equalsIgnoreCase(""))
            {
                finalPort= servicePort;
            }

        }
        else{
            transportAndHost = "http://localhost:";
            finalPort=servicePort;
        }
        return new ServiceParametersEntity(finalPort,transportAndHost);
    }



}