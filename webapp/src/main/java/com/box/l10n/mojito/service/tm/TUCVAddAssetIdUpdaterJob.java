package com.box.l10n.mojito.service.tm;

import com.box.l10n.mojito.service.DBUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * This is to update de-normalize the asset id.
 *
 * Need to check that the data is fully denormalized before starting to use logic that relies on it.
 *
 * @author jaurambault
 */
@Profile("!disablescheduling")
@Configuration
@Component
@DisallowConcurrentExecution
@ConditionalOnProperty(value="l10n.tucv-assetid-updater", havingValue = "true")
public class TUCVAddAssetIdUpdaterJob implements Job {

    /**
     * logger
     */
    static Logger logger = LoggerFactory.getLogger(TUCVAddAssetIdUpdaterJob.class);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DBUtils dbUtils;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        if (dbUtils.isMysql()) {
            logger.info("For Mysql only, update text unit current variant to de-normalize the asset id");

            int updateCount = 0;
            do {
                try {
                    updateCount = jdbcTemplate.update(""
                            + "update tm_text_unit_current_variant tucv, (\n"
                            + "    select tucv.id as tucv_id, tu.asset_id as asset_id\n"
                            + "    from tm_text_unit_current_variant tucv\n"
                            + "    inner join tm_text_unit as tu on tu.id = tucv.tm_text_unit_id\n"
                            + "    where \n"
                            + "        tucv.asset_id is null\n"
                            + "    limit 100000 \n"
                            + "    ) d\n"
                            + "set tucv.asset_id = d.asset_id "
                            + "where tucv.id = d.tucv_id and tucv.asset_id is null");

                    logger.info("TmTextUnitCurrentVariant update count: {}", updateCount);
                } catch (Exception e) {
                    logger.error("Couldn't update asset id, ignore", e);
                }
            } while (updateCount > 0 );
        } else {
            logger.trace("Don't support asset updates if not MySQL");
        }
    }

    @Bean(name = "jobDetailTUCVAssetIdUpdater")
    JobDetailFactoryBean jobDetailTUCVAssetIdUpdater() {
        JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
        jobDetailFactory.setJobClass(TUCVAddAssetIdUpdaterJob.class);
        jobDetailFactory.setDescription("Denormalize tm_text_unit_current_variant, update column: asset_id");
        jobDetailFactory.setDurability(true);
        return jobDetailFactory;
    }

    @Bean
    SimpleTriggerFactoryBean triggerTUCVAssetIdUpdater(@Qualifier("jobDetailTUCVAssetIdUpdater") JobDetail job) {
        SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
        trigger.setJobDetail(job);
        trigger.setRepeatInterval(Duration.ofMinutes(10).toMillis());
        trigger.setRepeatCount(2);
        return trigger;
    }

}
