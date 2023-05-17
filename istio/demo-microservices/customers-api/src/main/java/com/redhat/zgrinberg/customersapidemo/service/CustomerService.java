package com.redhat.zgrinberg.customersapidemo.service;

import com.redhat.zgrinberg.customersapidemo.model.Customer;

public interface CustomerService {

    Customer getCustomer(String customerId);
}
