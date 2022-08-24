package de.m3y.prometheus.exporter.fsimage;

import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.TransferFsImage;
import org.apache.hadoop.hdfs.server.namenode.ha.BootstrapStandby;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Watches fsimage for changes (filename version increment) and triggers update handler function.
 */
public class FsImageDownloader implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsImageDownloader.class);
    private final File fsImageDir;
    private File latestFsImageFile;
    private URL[] namenodeUris;
    private final Consumer<File> changeHandler;

    /**
     * Filters fsimage names.
     */
    static class FSImageFilenameFilter implements FilenameFilter {
        static final Pattern FS_IMAGE_PATTERN = Pattern.compile("fsimage_\\d+");

        @Override
        public boolean accept(File dir, String name) {
            return FS_IMAGE_PATTERN.matcher(name).matches();
        }
    }

    static final FilenameFilter FSIMAGE_FILTER = new FSImageFilenameFilter();

    /**
     * Sorts descending by file name.
     */
    static final Comparator<File> FSIMAGE_FILENAME_COMPARATOR = (o1, o2) -> o2.getName().compareTo(o1.getName());

    public FsImageDownloader(File fsImageDir, Consumer<File> changeHandler) {
        this.changeHandler = changeHandler;
        this.fsImageDir = fsImageDir;

        if (!fsImageDir.exists()) {
            throw new IllegalArgumentException(fsImageDir.getAbsolutePath() + " does not exist");
        }

        if (!fsImageDir.isDirectory()) {
            throw new IllegalArgumentException(fsImageDir.getAbsolutePath() + " is not a directory");
        }
    }

    @Override
    public void run() {
        try {
            File fsImageFile = downloadLatestFSImageFile(fsImageDir, namenodeUris);
            if (!fsImageFile.equals(latestFsImageFile)) {
                LOGGER.debug("Detected changes (old={}, new={})",
                        null == latestFsImageFile ? "" : latestFsImageFile.getAbsoluteFile(),
                        fsImageFile.getAbsoluteFile());
                latestFsImageFile = fsImageFile;
                // Notify
                changeHandler.accept(fsImageFile);
            } else {
                LOGGER.debug("Skipping previously discovered {}", fsImageFile.getAbsoluteFile());
            }
        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            LOGGER.warn("Can not download fsimage file : {}", ex.getMessage());
        }
    }

    static File downloadLatestFSImageFile(File fsImageDir, URL[] namenodeUris) throws IOException {
        // Check dir
        if (!fsImageDir.exists()) {
            throw new IllegalArgumentException("Directory " + fsImageDir.getAbsolutePath() + " for fsimage files does not exist");
        }
        File fsImageFile = new File(fsImageDir, "fsImage");
        LOGGER.info("Downloading fsimage from: {}, path: {}", namenodeUris[0], fsImageFile.getName());
        TransferFsImage.downloadMostRecentImageToDirectory(namenodeUris[0], fsImageFile);
        return fsImageFile;
    }
}
