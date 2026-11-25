package com.shoplab.retrieval.shared.constants;

/**
 * What the service sends to the model gateway so the logs of both can be crossed (LAB-84, FR-025). The id
 * is the {@code X-Request-Id} of the request, which the chat forwards (specs/LAB-129).
 */
public final class GatewayHeaders {

    /** The id of the request ({@code X-Request-Id}), as a header of every call to the gateway. */
    public static final String REQUEST_ID_HEADER = "x-shoplab-request-id";

    /** Key under which the request id travels in the Reactor context, from the filter to the model adapter. */
    public static final String REQUEST_ID_CONTEXT_KEY = "shoplab.gateway.requestId";

    private GatewayHeaders() {
    }
}
