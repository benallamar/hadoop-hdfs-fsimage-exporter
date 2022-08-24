package de.m3y.prometheus.exporter.fsimage;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import de.m3y.hadoop.hdfs.hfsa.util.IECBinary;

/**
 * Config options for collector.
 */
public class Config {
    /**
     * Default file size distribution bucket limits.
     */
    private static final List<String> DEFAULT_FILE_SIZE_DISTRIBUTION_BUCKETS = Collections.unmodifiableList(Arrays.asList("0", "1 MiB", "32 MiB", "64 MiB", "128 MiB", "1 GiB", "10 GiB"));

    /**
     * Path where HDFS NameNode stores fsimage file snapshots
     */
    private String fsImagePath;
    /**
     * A list of paths to report statistics for.
     * <p>
     * Paths can contain a regexp postfix, like "/users/ab.*", for matching direct child directories
     */
    private Set<String> paths;
    /**
     * Path sets are grouped paths by an identifier.
     *
     * @see #paths
     */
    private Map<String, List<String>> pathSets;

    /**
     * Skip file size distribution for group stats.
     */
    private boolean skipFileDistributionForGroupStats = false;
    /**
     * Skip file size distribution for user stats.
     */
    private boolean skipFileDistributionForUserStats = false;
    /**
     * Skip file size distribution for path based stats.
     */
    private boolean skipFileDistributionForPathStats = false;
    /**
     * Skip file size distribution for path set based stats.
     */
    private boolean skipFileDistributionForPathSetStats = false;
    /**
     * File size distribution buckets, supporting IEC units of KiB, MiB, GiB, TiB, PiB
     */
    private List<String> fileSizeDistributionBuckets = DEFAULT_FILE_SIZE_DISTRIBUTION_BUCKETS;

    /**
     * Download fsimage from namenode webservers
     */
    private boolean fetchFromRemoteNamenode = true;

    /**
     * Namenode to download url from!
     */
    private URL[] namenodeUrls;

    /**
     * Principal/Keytab to use to connect with namenode Spnego Service
     */
    private String principal;
    private String keytabPath;

    public String getPrincipal(){
        return principal;
    }

    public void setPrincipal(String principal){
        this.principal = principal;
    }

    public String getKeytabPath(){
        return principal;
    }

    public void setKeytabPath(String keytabPath){
        this.keytabPath = keytabPath;
    }

    public String getFsImagePath() {
        return fsImagePath;
    }

    public void setFsImagePath(String fsImagePath) {
        this.fsImagePath = fsImagePath;
    }

    public Set<String> getPaths() {
        return paths;
    }

    public void setPaths(Set<String> paths) {
        this.paths = paths;
    }

    public boolean hasPaths() {
        return null != paths && !paths.isEmpty();
    }

    public boolean isSkipFileDistributionForPathStats() {
        return skipFileDistributionForPathStats;
    }

    public boolean isFetchFromRemoteNamenode() {
        return fetchFromRemoteNamenode;
    }

    public void setFetchFromRemoteNamenode(boolean fetchFromRemoteNamenode) {
        this.fetchFromRemoteNamenode = fetchFromRemoteNamenode;
    }

    public URL[] getNamenodeUrls() {
        return namenodeUrls;
    }

    public void setNamenodeUris(String[] namenodes) throws MalformedURLException {
        URL[] namenodeUrls = new URL[namenodes.length];
        for (int i = 0; i < namenodes.length; i++) {
            namenodeUrls[i] = new URL(namenodes[i]);
        }
        this.namenodeUrls = namenodeUrls;
    }

    public void setSkipFileDistributionForPathStats(boolean skipFileDistributionForPathStats) {
        this.skipFileDistributionForPathStats = skipFileDistributionForPathStats;
    }

    public boolean isSkipFileDistributionForGroupStats() {
        return skipFileDistributionForGroupStats;
    }

    public void setSkipFileDistributionForGroupStats(boolean skipFileDistributionForGroupStats) {
        this.skipFileDistributionForGroupStats = skipFileDistributionForGroupStats;
    }

    public boolean isSkipFileDistributionForUserStats() {
        return skipFileDistributionForUserStats;
    }

    public void setSkipFileDistributionForUserStats(boolean skipFileDistributionForUserStats) {
        this.skipFileDistributionForUserStats = skipFileDistributionForUserStats;
    }

    public Map<String, List<String>> getPathSets() {
        return pathSets;
    }

    public void setPathSets(Map<String, List<String>> pathSets) {
        this.pathSets = pathSets;
    }

    public boolean hasPathSets() {
        return pathSets != null && !pathSets.isEmpty();
    }

    public boolean isSkipFileDistributionForPathSetStats() {
        return skipFileDistributionForPathSetStats;
    }

    public void setSkipFileDistributionForPathSetStats(boolean skipFileDistributionForPathSetStats) {
        this.skipFileDistributionForPathSetStats = skipFileDistributionForPathSetStats;
    }

    public List<String> getFileSizeDistributionBuckets() {
        return fileSizeDistributionBuckets;
    }

    public void setFileSizeDistributionBuckets(List<String> fileSizeDistributionBuckets) {
        this.fileSizeDistributionBuckets = fileSizeDistributionBuckets;
    }

    public double[] getFileSizeDistributionBucketsAsDoubles() {
        return getFileSizeDistributionBuckets().stream().mapToDouble(IECBinary::parse).toArray();
    }
}
