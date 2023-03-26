package com.redhat;

public interface InvokeMicroservice {

    MyResponseEntity run(String path, String port, String transportAndHost);
}
