package com.staraxis.game.shared.world.stellar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorldGenDiagnostics implements Serializable {

    private int repairAttemptCount;
    private List<String> messages;
    private Map<String, String> details;

    public WorldGenDiagnostics() {
        this.messages = new ArrayList<>();
        this.details = new LinkedHashMap<>();
    }

    public int getRepairAttemptCount() {
        return repairAttemptCount;
    }

    public void setRepairAttemptCount(int repairAttemptCount) {
        if (repairAttemptCount < 0) {
            throw new IllegalArgumentException("repairAttemptCount 必须 >= 0");
        }
        this.repairAttemptCount = repairAttemptCount;
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void setMessages(List<String> messages) {
        if (messages == null) {
            this.messages = new ArrayList<>();
            return;
        }
        this.messages = new ArrayList<>(messages);
    }

    public void addMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        this.messages.add(message);
    }

    public Map<String, String> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    public void setDetails(Map<String, String> details) {
        if (details == null) {
            this.details = new LinkedHashMap<>();
            return;
        }
        this.details = new LinkedHashMap<>(details);
    }

    public void putDetail(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            this.details.remove(key);
            return;
        }
        this.details.put(key, value);
    }
}
