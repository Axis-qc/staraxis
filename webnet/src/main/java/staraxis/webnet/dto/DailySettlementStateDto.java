package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DailySettlementStateDto
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailySettlementStateDto {

    public final int settledDay;
    public final int sectorCount;

    public DailySettlementStateDto(int settledDay, int sectorCount) {
        this.settledDay = settledDay;
        this.sectorCount = sectorCount;
    }
}
