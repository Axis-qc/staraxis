package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandResultMessageDto {

    public final String type = "command_result";
    public final String clientCommandId;
    public final long entityId;
    public final long simulationTick;
    public final String resultType;
    public final double gameSeconds;
    public final String reason;
    public final Map<String, Object> correctionData;

    public CommandResultMessageDto(
            String clientCommandId,
            long entityId,
            long simulationTick,
            String resultType,
            double gameSeconds,
            String reason,
            Map<String, Object> correctionData) {
        this.clientCommandId = clientCommandId;
        this.entityId = entityId;
        this.simulationTick = simulationTick;
        this.resultType = resultType;
        this.gameSeconds = gameSeconds;
        this.reason = reason;
        this.correctionData = correctionData;
    }

    public static CommandResultMessageDto forSubmitted(String clientCommandId, long entityId, long simulationTick) {
        return new CommandResultMessageDto(clientCommandId, entityId, simulationTick, "submitted", 0.0, null, null);
    }

    public static CommandResultMessageDto forAccepted(String clientCommandId, long entityId, long simulationTick) {
        return new CommandResultMessageDto(clientCommandId, entityId, simulationTick, "accepted", 0.0, null, null);
    }

    public static CommandResultMessageDto forRejected(String clientCommandId, long entityId, long simulationTick, String reason) {
        return new CommandResultMessageDto(clientCommandId, entityId, simulationTick, "rejected", 0.0, reason, null);
    }

    public static CommandResultMessageDto forCompleted(String clientCommandId, long entityId, long simulationTick, double gameSeconds) {
        return new CommandResultMessageDto(clientCommandId, entityId, simulationTick, "completed", gameSeconds, null, null);
    }

    public static CommandResultMessageDto forCorrected(String clientCommandId, long entityId, long simulationTick, double gameSeconds, String reason, Map<String, Object> correctionData) {
        return new CommandResultMessageDto(clientCommandId, entityId, simulationTick, "corrected", gameSeconds, reason, correctionData);
    }
}
