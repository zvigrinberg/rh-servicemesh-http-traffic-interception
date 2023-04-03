package com.redhat.serviceinterceptor;

import lombok.Data;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Data
public class SharedBuffer {

    private Map<String, Map> mapOfKeys = new HashMap<>();

}
