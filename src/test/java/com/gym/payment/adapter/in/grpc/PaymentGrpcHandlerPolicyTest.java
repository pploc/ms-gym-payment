package com.gym.payment.adapter.in.grpc;

import com.gym.common.grpc.interceptor.GrpcMethodRegistry;
import com.gym.payment.application.PaymentApplicationService;
import com.gym.proto.payment.v1.PaymentServiceGrpc;
import io.grpc.BindableService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGrpcHandlerPolicyTest {

    @Test
    void given_frozen_payment_service_when_registering_methods_then_every_method_has_a_policy() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        PaymentGrpcHandler handler = new PaymentGrpcHandler(mock(PaymentApplicationService.class));
        when(applicationContext.getBeansOfType(BindableService.class)).thenReturn(Map.of("paymentGrpcHandler", handler));
        GrpcMethodRegistry registry = new GrpcMethodRegistry(applicationContext);

        registry.onApplicationEvent(mock(ContextRefreshedEvent.class));

        PaymentServiceGrpc.getServiceDescriptor().getMethods().forEach(method -> assertNotNull(registry.getPolicy(method.getFullMethodName())));
    }
}
