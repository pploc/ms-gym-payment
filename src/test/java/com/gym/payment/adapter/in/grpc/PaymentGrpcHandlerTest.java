package com.gym.payment.adapter.in.grpc;

import com.gym.payment.application.PaymentApplicationService;
import com.gym.proto.payment.v1.GetSpendingHistoryRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentGrpcHandlerTest {

    @Test
    void given_inactive_frozen_rpc_when_called_then_returns_unimplemented() {
        PaymentGrpcHandler handler = new PaymentGrpcHandler(mock(PaymentApplicationService.class));
        @SuppressWarnings("unchecked")
        StreamObserver<com.gym.proto.payment.v1.GetSpendingHistoryResponse> observer = mock(StreamObserver.class);

        handler.getSpendingHistory(GetSpendingHistoryRequest.getDefaultInstance(), observer);

        org.mockito.ArgumentCaptor<Throwable> error = org.mockito.ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(error.getValue()).getCode());
    }
}
