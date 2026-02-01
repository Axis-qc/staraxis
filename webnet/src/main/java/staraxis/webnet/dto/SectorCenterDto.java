package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SectorCenterDto
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectorCenterDto {

    public final int q;
    public final int r;
    public final double x;
    public final double y;

    public SectorCenterDto(int q, int r, double x, double y) {
        this.q = q;
        this.r = r;
        this.x = x;
        this.y = y;
    }
}
