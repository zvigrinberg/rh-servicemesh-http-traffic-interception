package com.redhat.zgrinberg.customersapidemo.controller;

import com.redhat.zgrinberg.customersapidemo.model.Customer;
import com.redhat.zgrinberg.customersapidemo.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    @GetMapping("{id}")
    public Customer getOne(@PathVariable String id)
    {
        return customerService.getCustomer(id);
    }
}
