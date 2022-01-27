package de.flashheart.rlgagent.jobs;

import de.flashheart.rlgagent.RLGAgent;
import lombok.extern.log4j.Log4j2;
import org.quartz.*;

@Log4j2
@DisallowConcurrentExecution
public class NetworkMonitoringJob implements InterruptableJob {

    public static final String name = "networkdebug";

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        try {
            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
            RLGAgent rlgAgent = (RLGAgent) jobExecutionContext.getScheduler().getContext().get("rlgAgent");
            rlgAgent.show_connection_status();
        } catch (SchedulerException e) {
            log.fatal(e);
            System.exit(0);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.info("job '{}' interrupted", name);
        // nothing to do here
    }

}
