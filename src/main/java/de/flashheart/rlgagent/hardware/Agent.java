package de.flashheart.rlgagent.hardware;

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


    public Agent(JSONObject jsonObject) {
        this.agentid = jsonObject.getString("agentid");
        this.gameid = jsonObject.getString("gameid");
        this.lastheartbeat = JavaTimeConverter.from_iso8601(jsonObject.getString("timestamp"));
        this.wifi = jsonObject.getInt("wifi");
    }

    public JSONObject toJson() {
        return new JSONObject().put("agentid", agentid)
                .put("gameid", gameid)
                .put("timestamp", JavaTimeConverter.to_iso8601(lastheartbeat))
                .put("wifi", wifi);
    }

    public String getMqttClientId(){
        return "mqtt-"+agentid;
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
