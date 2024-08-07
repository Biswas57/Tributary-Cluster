package tributary.api;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import tributary.core.typeHandlerFactory.TypeHandler;
import tributary.core.typeHandlerFactory.TypeHandlerFactory;

public class Message<T> extends TributaryObject {
    private LocalDateTime createdDate;
    private String payloadType;
    private Map<String, T> content;

    public Message(String messageId, LocalDateTime createdDate, String payloadType, Map<String, T> content) {
        super(messageId);
        this.createdDate = createdDate;
        this.payloadType = payloadType;
        this.content = content;
    }

    public static <T> Message<T> fromJson(JSONObject json, Class<T> type) {
        String messageId = json.getString("eventId");
        LocalDateTime createdDate = LocalDateTime.now();
        String payloadType = json.getString("PayloadType");

        JSONObject messageContent = json.getJSONObject("messageContents");
        Map<String, T> content = new HashMap<>();

        TypeHandler<T> handler = TypeHandlerFactory.getHandler(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported type: " + type.getSimpleName());
        }

        for (String key : messageContent.keySet()) {
            Object value = messageContent.get(key);
            content.put(key, handler.handle(value));
        }

        return new Message<>(messageId, createdDate, payloadType, content);
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public Map<String, T> getContent() {
        return content;
    }
}
