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
    private LocalDateTime lastheartbeat;
    private int wifi;


    public Agent(JSONObject jsonObject) {
        this.agentid = jsonObject.getString("agentid");
        this.lastheartbeat = JavaTimeConverter.from_iso8601(jsonObject.getString("timestamp"));
        this.wifi = jsonObject.getInt("wifi");
    }

    public JSONObject toJson() {
        return new JSONObject().put("agentid", agentid)
                .put("timestamp", JavaTimeConverter.to_iso8601(lastheartbeat))
                .put("wifi", wifi);
    }

    /**
     * creates a unique but readable client id. if it is not unique the client will
     * behave insane, once the connection has been lost and re-established.
     * it was quite painful finding this one out.
     * @return
     */
    public String getMqttClientId() {
        return "mqtt-" + (int) (Math.random() * 10000) + "-" + agentid;
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
