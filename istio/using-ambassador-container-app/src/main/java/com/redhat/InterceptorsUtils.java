package com.redhat;

import java.util.Set;
import java.util.stream.Collectors;

public class InterceptorsUtils {
    public static String findServicePort() {
        String result = "";
        String keyWithServiceName ="";
        String podName =  System.getenv().get("POD_NAME");
        Set<String> env_keys = System.getenv().keySet();
        Set<String> envServicePortKeys = env_keys.stream().filter(s -> s.endsWith("SERVICE_PORT") && !s.contains("KUBERNETES")).collect(Collectors.toSet());
        for (String key : envServicePortKeys) {
            int endOfServiceName = key.indexOf("SERVICE_PORT");
            String serviceName = key.substring(0, endOfServiceName);
            serviceName = serviceName.toLowerCase().replace("_","-");
            if (podName.startsWith(serviceName))
            {
                keyWithServiceName = key;
                break;
            }

        }
        if (!keyWithServiceName.trim().equalsIgnoreCase(""))
        {
            result = System.getenv().get(keyWithServiceName);
        }


        return result;
    }

}

