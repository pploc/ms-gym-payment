package com.gym.payment.adapter.in.grpc;

import com.gym.common.grpc.security.RequirePolicy;
import com.gym.common.grpc.security.RpcPolicyKind;
import com.gym.payment.application.PaymentApplicationService;
import com.gym.proto.payment.v1.GetPaymentStatusRequest;
import com.gym.proto.payment.v1.GetPaymentStatusResponse;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import com.gym.proto.payment.v1.PaymentServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentGrpcHandler extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentApplicationService paymentService;

    @Override
    @RequirePolicy(RpcPolicyKind.INTERNAL_WORKLOAD)
    public void initiatePayment(
            InitiatePaymentRequest request, StreamObserver<InitiatePaymentResponse> responseObserver) {
        try {
            responseObserver.onNext(paymentService.initiate(request));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @RequirePolicy(RpcPolicyKind.INTERNAL_WORKLOAD)
    public void getPaymentStatus(
            GetPaymentStatusRequest request, StreamObserver<GetPaymentStatusResponse> responseObserver) {
        try {
            PaymentApplicationService.PaymentStatusView payment = paymentService.getStatus(request.getPaymentId());
            responseObserver.onNext(GetPaymentStatusResponse.newBuilder()
                    .setPaymentId(request.getPaymentId())
                    .setStatus(payment.status())
                    .setAmountVnd(payment.intentAmountVnd())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
