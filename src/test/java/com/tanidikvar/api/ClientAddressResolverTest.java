package com.tanidikvar.api;

import com.tanidikvar.api.auth.security.ClientAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressResolverTest {
    @Test void ignoresForwardingHeaderFromUntrustedPeer() {
        var request = request("203.0.113.10", "198.51.100.20");
        assertThat(new ClientAddressResolver("10.0.0.0/8").resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test void removesTrustedProxiesFromRightOfForwardingChain() {
        var request = request("10.0.0.2", "198.51.100.20, 10.0.0.1");
        assertThat(new ClientAddressResolver("10.0.0.0/8").resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test void supportsIpv6Networks() {
        var request = request("2001:db8::2", "2001:4860:4860::8888");
        assertThat(new ClientAddressResolver("2001:db8::/32").resolve(request)).isEqualTo("2001:4860:4860:0:0:0:0:8888");
    }

    private MockHttpServletRequest request(String peer, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
