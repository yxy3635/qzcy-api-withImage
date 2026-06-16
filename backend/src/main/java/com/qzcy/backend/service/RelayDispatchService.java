package com.qzcy.backend.service;

import com.qzcy.backend.dto.relay.RelayDispatchRequest;
import com.qzcy.backend.dto.relay.RelayDispatchResult;
import com.qzcy.backend.dto.relay.RelayStreamDispatchResult;

public interface RelayDispatchService {
    RelayDispatchResult dispatch(RelayDispatchRequest request) throws Exception;
    RelayStreamDispatchResult dispatchStream(RelayDispatchRequest request) throws Exception;
}
