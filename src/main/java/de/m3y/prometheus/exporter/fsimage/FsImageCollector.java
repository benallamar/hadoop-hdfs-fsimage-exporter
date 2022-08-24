package de.m3y.prometheus.exporter.fsimage;

import java.io.File;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects stats from Hadoop FSImage.
 * <p>
 * Note:
 * <ul>
 * <li>A background thread watches and parses FSImage, therefore not blocking metrics collection itself.
 * Parse time depends on FSImage size, and can be up to minutes.
 * <p>
 * See {@link FsImageWatcher}
 * </li>
 * </ul>
 */
public class FsImageCollector extends Collector {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsImageCollector.class);

    static final String METRIC_PREFIX = "fsimage_";

    private final Counter scapeRequests = Counter.build().name(METRIC_PREFIX + "scrape_requests_total").help("Exporter requests made").create();
    private final Counter scrapeErrors = Counter.build().name(METRIC_PREFIX + "scrape_errors_total").help("Counts failed scrapes.").create();
    private final Gauge scrapeDuration = Gauge.build().name(METRIC_PREFIX + "scrape_duration_seconds").help("Scrape duration").create();

    private final FsImageUpdateHandler fsImageReportUpdater;


    private final ScheduledExecutorService scheduler;

    FsImageCollector(Config config) throws IOException {
        final String path = config.getFsImagePath();
        if (null == path || path.isEmpty()) {
            throw new IllegalArgumentException("Please");
        }
        scheduler = Executors.newScheduledThreadPool(1);
        fsImageReportUpdater = new FsImageUpdateHandler(config);

        if (!config.isFetchFromRemoteNamenode()) {
            File fsImageDir = new File(path);
            if (!fsImageDir.exists()) {
                throw new IllegalArgumentException("The directory for FSImage snapshots (fsImagePath) " + fsImageDir.getAbsolutePath() + " does not exist");
            }
            FsImageWatcher fsImageWatcher = new FsImageWatcher(fsImageDir, fsImageReportUpdater::onFsImageChange);
            scheduler.scheduleWithFixedDelay(fsImageWatcher, 0 /* Trigger immediately */, 60, TimeUnit.SECONDS);
        } else {
            if (config.getNamenodeUrls().length == 0) {
                throw new IllegalArgumentException("Remote option is active but no namenode uris has been provided!");
            }
            File fsImageDir = new File("/tmp/images");// Generate a random path to store image on it
            String principal = config.getPrincipal(), keytab = config.getKeytabPath();
            LOGGER.info("Using principal: {}, keytab path: {}", principal, keytab);
            if (keytab == null || principal == null) {
                throw new IllegalArgumentException("Please check that bother principal and keytab are given");
            }
            UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(principal, keytab);
            FsImageDownloader fsImageDownloader = new FsImageDownloader(fsImageDir, fsImageReportUpdater::onFsImageChange);
            ugi.doAs(new PrivilegedAction() {
                @Override
                public void run() {
                    scheduler.scheduleWithFixedDelay(fsImageDownloader, 0 /* Trigger immediately */, 60, TimeUnit.SECONDS);
                }
            });
        }
    }

    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();

        try (Gauge.Timer timer = scrapeDuration.startTimer()) {
            scapeRequests.inc();

            if (fsImageReportUpdater.collectFsImageSamples(mfs)) {
                scrapeErrors.inc();
            }
        } catch (Exception e) {
            scrapeErrors.inc();
            LOGGER.error("FSImage scrape failed", e);
        }

        mfs.addAll(scrapeDuration.collect());
        mfs.addAll(scapeRequests.collect());
        mfs.addAll(scrapeErrors.collect());

        return mfs;
    }

    /**
     * Closes resources such as scheduler for background parsing thread.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

}

