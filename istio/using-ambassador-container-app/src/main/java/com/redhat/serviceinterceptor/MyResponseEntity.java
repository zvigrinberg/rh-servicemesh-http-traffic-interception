package com.redhat.serviceinterceptor;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.jboss.resteasy.specimpl.MultivaluedTreeMap;

import java.util.Map;

@Data
@NoArgsConstructor
public class MyResponseEntity {
    private String responseBody;
    private MultivaluedTreeMap<String,Object> headers;

}
