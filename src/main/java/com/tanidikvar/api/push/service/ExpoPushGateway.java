package com.tanidikvar.api.push.service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ExpoPushGateway implements PushGateway {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final String accessToken;
    public ExpoPushGateway(ObjectMapper mapper, @Value("${app.push.access-token:}") String accessToken) {
        this.mapper=mapper; this.accessToken=accessToken;
    }
    public Result send(String token, String targetUrl) {
        var data=post("send", Map.of("to",token,"title","TanıdıkVar","body","Yeni bir bildirimin var.",
                "sound","default","channelId","activity","data",Map.of("url",targetUrl))).path("data");
        if(data.isArray()) data=data.path(0);
        return result(data,true);
    }
    public Result receipt(String ticket) {
        return result(post("getReceipts",Map.of("ids",java.util.List.of(ticket))).path("data").path(ticket),false);
    }
    private Result result(JsonNode data, boolean ticket) {
        if(data.path("status").asText().equals("ok")) {
            String id=data.path("id").asText();
            if(ticket && (id.isBlank() || id.length()>100)) return new Result("RETRY",null,"INVALID_RESPONSE");
            return new Result(ticket?"TICKET":"SENT",ticket?id:null,null);
        }
        String error=data.path("details").path("error").asText();
        return switch(error) {
            case "DeviceNotRegistered" -> new Result("FAILED",null,"DEVICE_NOT_REGISTERED");
            case "MessageTooBig", "MismatchSenderId", "InvalidCredentials" -> new Result("FAILED",null,"PROVIDER_CONFIGURATION");
            default -> new Result("RETRY",null,"PROVIDER_UNAVAILABLE");
        };
    }
    private JsonNode post(String action,Object body) {
        var request=HttpRequest.newBuilder(URI.create("https://exp.host/--/api/v2/push/"+action))
                .timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Accept","application/json");
        if(!accessToken.isBlank()) request.header("Authorization","Bearer "+accessToken);
        try {
            var response=http.send(request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) return mapper.createObjectNode();
            return mapper.readTree(response.body());
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt(); return mapper.createObjectNode();
        } catch(java.io.IOException | RuntimeException exception) {
            // Provider payloads and tokens must never reach logs or public exception messages.
            return mapper.createObjectNode();
        }
    }
}
