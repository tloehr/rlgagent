package de.flashheart.rlgagent.hardware;

import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.misc.JavaTimeConverter;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONObject;

import java.time.LocalDateTime;

@Getter
@Setter
public class Agent {


    private String agentid;
    private String gameid;
    private LocalDateTime lastheartbeat;
    private int wifi;
    private boolean has_siren;
    private boolean has_leds;
    private boolean has_sound;
    private boolean has_line_display;
    private boolean has_matrix_display;
    private boolean has_rfid;

    public Agent(JSONObject jsonObject) {
        this.agentid = jsonObject.getString("agentid");
        this.gameid = jsonObject.getString("gameid");
        this.lastheartbeat = JavaTimeConverter.from_iso8601(jsonObject.getString("timestamp"));
        this.wifi = jsonObject.getInt("wifi");
        this.has_siren = jsonObject.getBoolean(Configs.HAS_SIRENS);
        this.has_leds = jsonObject.getBoolean(Configs.HAS_LEDS);
        this.has_sound = jsonObject.getBoolean(Configs.HAS_SOUND);
        this.has_line_display = jsonObject.getBoolean(Configs.HAS_LINE_DISPLAY);
        this.has_matrix_display = jsonObject.getBoolean(Configs.HAS_MATRIX_DISPLAY);
        this.has_rfid = jsonObject.getBoolean(Configs.HAS_RFID);
    }

    public JSONObject toJson() {
        return new JSONObject().put("agentid", agentid)
                .put("gameid", gameid)
                .put("timestamp", JavaTimeConverter.to_iso8601(lastheartbeat))
                .put("wifi", wifi)
                .put(Configs.HAS_SIRENS, has_siren)
                .put(Configs.HAS_LEDS, has_leds)
                .put(Configs.HAS_LINE_DISPLAY, has_line_display)
                .put(Configs.HAS_MATRIX_DISPLAY, has_matrix_display)
                .put(Configs.HAS_SOUND, has_sound)
                .put(Configs.HAS_RFID, has_rfid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Agent agent = (Agent) o;

        return new EqualsBuilder().append(getAgentid(), agent.getAgentid()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(getAgentid()).toHashCode();
    }

}
