package com.redhat.zgrinberg.customersapidemo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Customer {

    private String id;
    private String firstName;
    private String lastName;
    private Integer age;
    private String accessToken;
    private String secret;



}
