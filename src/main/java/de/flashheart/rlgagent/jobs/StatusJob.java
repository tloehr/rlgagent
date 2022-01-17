package de.flashheart.rlgagent.jobs;

import de.flashheart.rlgagent.RLGAgent;
import lombok.extern.log4j.Log4j2;
import org.quartz.*;

@Log4j2
@DisallowConcurrentExecution
public class StatusJob implements Job, InterruptableJob {
    public static final String name = "statusreport";

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        try {
            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
            RLGAgent rlgAgent = (RLGAgent) jobExecutionContext.getScheduler().getContext().get("rlgAgent");
            rlgAgent.procStatus();
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
