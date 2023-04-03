package com.redhat.serviceinterceptor;

public interface InvokeMicroservice {

    MyResponseEntity run(String path, String port, String transportAndHost);
}
