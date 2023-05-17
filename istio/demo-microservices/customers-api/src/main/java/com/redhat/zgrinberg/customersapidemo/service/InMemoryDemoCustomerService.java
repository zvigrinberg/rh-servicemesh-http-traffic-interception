package com.redhat.zgrinberg.customersapidemo.service;

import com.redhat.zgrinberg.customersapidemo.model.Customer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class InMemoryDemoCustomerService implements CustomerService {
    @Override
    public Customer getCustomer(String customerId) {
        return new Customer(customerId,"John","doe",50,"someSecretAccessTokenValue","someConfidentialSecretValue");
    }
}
