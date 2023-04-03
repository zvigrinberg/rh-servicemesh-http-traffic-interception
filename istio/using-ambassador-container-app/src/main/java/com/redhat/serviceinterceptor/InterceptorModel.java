package com.redhat.serviceinterceptor;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InterceptorModel {
    private String countryCode;
    private String dataOwningCountryCode;
    private String manifestName;
    private String jobType;
    private String dataSet;
    private boolean protectNullValues;
    private boolean preserveStringLength;
    private String  snapshotDate;
    private String restrictedText;
}
