package com.redhat.serviceinterceptor;

import org.jboss.logging.Logger;
import org.jboss.resteasy.specimpl.MultivaluedTreeMap;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

@Named("InvokeMicroserviceImpl")
@Singleton
public class InvokeMicroserviceImpl implements InvokeMicroservice {

    private final Logger logger = Logger.getLogger("InvokeMicroserviceImpl.java");
    private final Client client = ClientBuilder.newClient();
    @Override
    public MyResponseEntity run(String path, String port, String transportAndHost) {
        WebTarget msURL = client.target(transportAndHost + port + path);
        Response response = msURL.request().post(null);
        MyResponseEntity myResponseEntity = new MyResponseEntity();

        String result = response.readEntity(String.class);
        myResponseEntity.setResponseBody(result);
        myResponseEntity.setHeaders((MultivaluedTreeMap<String, Object>) response.getHeaders());
        return myResponseEntity;





    }
}
