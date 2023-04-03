package com.redhat.serviceinterceptor;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServiceParametersEntity {
    private String finalPort;
    private String transportAndHost;
}
