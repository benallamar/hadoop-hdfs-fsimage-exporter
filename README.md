Prometheus Hadoop HDFS FSImage Exporter
=======

[![Maven Central](https://img.shields.io/maven-central/v/de.m3y.prometheus.exporter.fsimage/fsimage-exporter.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22de.m3y.prometheus.exporter.fsimage%22%20AND%20a%3A%22fsimage-exporter%22) | [Docker Hub](https://hub.docker.com/r/marcelmay/hadoop-hdfs-fsimage-exporter/)

Exports Hadoop HDFS statistics to [Prometheus monitoring](https://prometheus.io/) including
* total / per user / per group / per configured directory path / per set of paths 
    * number of directories
    * number of files
    * file size and optionally size distribution
    * number of blocks
    * file replication (overall / per user summary)
    
The exporter parses the FSImage using the [Hadoop FSImage Analysis library](https://github.com/marcelmay/hfsa).
This approach has the advantage of
* being fast (2.6 GB FSImage ~ 50s)
* adding no heavy additional load to HDFS NameNode (no NameNode queries, you can run it on 2nd NameNode)

The disadvantage is
* no real time update, only about every 6h when NameNode writes FSImage. But should be sufficient for most cases (long term trend, detecting HDFS small file abuses, user and group stats)
* parsing takes 2x-3x FSImage size in heap space

![FSImage Exporter overview](fsimage_exporter.png)

The exporter parses fsimages in background thread which checks every 60s for fsimage changes.
This avoids blocking and long running Prometheus scrapes.

## Requirements
For building:
* JDK 8
* [Maven 3.5.x](http://maven.apache.org)
* Docker 1.6+ (only required if building docker image)

For running:
* JRE 8 for running
* Access to Hadoop FSImage file
* Docker 1.6+ (only required if building docker image)

## Downloading

Available on [![Maven Central](https://img.shields.io/maven-central/v/de.m3y.prometheus.exporter.fsimage/fsimage-exporter.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22de.m3y.prometheus.exporter.fsimage%22%20AND%20a%3A%22fsimage-exporter%22) and as docker image on [Docker Hub](https://hub.docker.com/r/marcelmay/hadoop-hdfs-fsimage-exporter/).

## Building

```mvn clean install```

You can test the exporter using [run_example.sh](run_example.sh) after building.

For building including docker image, run:

```mvn clean install -Pdocker```

You can run the docker image via maven, too:

```mvn clean install docker:run -Pdocker```

Or directly using docker command line

```docker run -i -t -p 7772:7772 -v $PWD/src/test/resources:/fsimage-location -e "JAVA_OPTS=-server -XX:+UseG1GC -Xmx1024m" marcelmay/hadoop-hdfs-fsimage-exporter```

When running the docker image via Maven, docker will mount the projects src/test/resources directory (with test fsimage) and expose the exporter on http://0.0.0.0:7772/ .

## Installation and configuration

* Install the JAR on a system where the FSImage is locally available (eg name node server).

* Configure the exporter     
  Create a yml file (see [example.yml](example.yml)):
  ```
  # Path where HDFS NameNode stores the fsimage files
  # See https://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-hdfs/hdfs-default.xml#dfs.namenode.name.dir
  fsImagePath : 'src/test/resources'
  
  # Skip file size distribution for group based stats
  skipFileDistributionForGroupStats : true
  
  # Skip file size distribution for user based stats
  # Enable for figuring out who has too many small files.
  skipFileDistributionForUserStats : false
  
  # Compute per path stats
  # Supports regex matching for direct child directories
  paths:
    - '/tmp'
    - '/datalake/a.*'
    - '/user/m.*'
    
  # Skip file size distribution for path based stats
  skipFileDistributionForPathStats : true
  
  # Path sets are grouped paths by an identifier.
  # The exporter computes for each identifier the stats.
  # Compared to simple "paths" above, this allows to specify several paths for one stat computation.
  pathSets:
    'userMmAndFooAndAsset1' : [
      '/datalake/asset3',
      '/user/mm',
      '/user/foo'
      ]
    'datalakeAsset1and2' : [
      '/datalake/asset1',
    '/datalake/asset2'
    ]
    
  # Skip file size distribution for path sets based stats
  skipFileDistributionForPathSetStats : true
  ```
  Note that the flag toggling file size distribution switches between [Summary](https://github.com/prometheus/client_java#summary) (few time series)
  and [Histogram](https://github.com/prometheus/client_java#histogram) (many time series)
 
* Run the exporter
  ```
    > java -jar target/fsimage-exporter.jar
    Usage: WebServer <hostname> <port> <yml configuration file>
  ```
  Example JVM opts (-Xmx max heap depends on your fsimage size): 
  ```
  > java -Xmx1024m -dsa -server -XX:+UseG1GC \
         -jar target/fsimage-exporter-1.0-SNAPSHOT.jar \
         0.0.0.0 9092 example.yml
  ```
  Note: Make sure to size the heap correctly. As an heuristic, you can use 3 * fsimage size.
  
* Test the exporter  
  Open http://\<hostname>:\<port>/metrics or http://\<hostname>:\<port>/ (for configuration overview)
   
* Add to prometheus
  ```
  - job_name: 'fsimage'
      scrape_interval: 180m # Depends on how often the name node writes a fsimage file.
      scrape_timeout:  200s # Depends on size
      static_configs:
        - targets: ['<hostname>:<port>']
          labels:
            ...
  ```
  Note:  
  For Grafana, you want to sample more often with a scrape interval of minutes.
  The exporter caches previously parsed FSImage, so it is a fast operation.


## Roadmap

Release 1.3+ (see [issues](../../issues)):
* Example Grafana dashboard?

## Example output

### Example home output

![Home output](home.png)

### Example metrics
Here's the example output for the [test fsimage](src/test/resources/fsimage_0001), using [example.yml](example.yml) configuration:

```
# HELP fsimage_path_fsize Path specific file size and file count
# TYPE fsimage_path_fsize summary
fsimage_path_fsize_count{path="/datalake/asset2",} 2.0
fsimage_path_fsize_sum{path="/datalake/asset2",} 2098176.0
fsimage_path_fsize_count{path="/datalake/asset3",} 3.0
fsimage_path_fsize_sum{path="/datalake/asset3",} 6291456.0
fsimage_path_fsize_count{path="/user/mm",} 0.0
fsimage_path_fsize_sum{path="/user/mm",} 0.0
fsimage_path_fsize_count{path="/datalake/asset1",} 0.0
fsimage_path_fsize_sum{path="/datalake/asset1",} 0.0
# HELP fsimage_user_fsize Per user file size distribution
# TYPE fsimage_user_fsize histogram
fsimage_user_fsize_bucket{user_name="root",le="0.0",} 0.0
fsimage_user_fsize_bucket{user_name="root",le="1048576.0",} 1.0
fsimage_user_fsize_bucket{user_name="root",le="3.3554432E7",} 1.0
fsimage_user_fsize_bucket{user_name="root",le="6.7108864E7",} 1.0
fsimage_user_fsize_bucket{user_name="root",le="1.34217728E8",} 1.0
fsimage_user_fsize_bucket{user_name="root",le="1.073741824E9",} 1.0
fsimage_user_fsize_bucket{user_name="root",le="1.073741824E10",} 1.0
fsimage_user_fsize_bucket{user_name="root",le="+Inf",} 1.0
fsimage_user_fsize_count{user_name="root",} 1.0
fsimage_user_fsize_sum{user_name="root",} 1024.0
fsimage_user_fsize_bucket{user_name="foo",le="0.0",} 0.0
fsimage_user_fsize_bucket{user_name="foo",le="1048576.0",} 0.0
fsimage_user_fsize_bucket{user_name="foo",le="3.3554432E7",} 0.0
fsimage_user_fsize_bucket{user_name="foo",le="6.7108864E7",} 0.0
fsimage_user_fsize_bucket{user_name="foo",le="1.34217728E8",} 0.0
fsimage_user_fsize_bucket{user_name="foo",le="1.073741824E9",} 1.0
fsimage_user_fsize_bucket{user_name="foo",le="1.073741824E10",} 1.0
fsimage_user_fsize_bucket{user_name="foo",le="+Inf",} 1.0
fsimage_user_fsize_count{user_name="foo",} 1.0
fsimage_user_fsize_sum{user_name="foo",} 1.6777216E8
fsimage_user_fsize_bucket{user_name="mm",le="0.0",} 0.0
fsimage_user_fsize_bucket{user_name="mm",le="1048576.0",} 3.0
fsimage_user_fsize_bucket{user_name="mm",le="3.3554432E7",} 12.0
fsimage_user_fsize_bucket{user_name="mm",le="6.7108864E7",} 13.0
fsimage_user_fsize_bucket{user_name="mm",le="1.34217728E8",} 14.0
fsimage_user_fsize_bucket{user_name="mm",le="1.073741824E9",} 14.0
fsimage_user_fsize_bucket{user_name="mm",le="1.073741824E10",} 14.0
fsimage_user_fsize_bucket{user_name="mm",le="+Inf",} 14.0
fsimage_user_fsize_count{user_name="mm",} 14.0
fsimage_user_fsize_sum{user_name="mm",} 1.8863616E8
# HELP fsimage_compute_stats_duration_seconds Time for computing stats for a loaded FSImage
# TYPE fsimage_compute_stats_duration_seconds summary
fsimage_compute_stats_duration_seconds_count 1.0
fsimage_compute_stats_duration_seconds_sum 0.069411349
# HELP fsimage_path_blocks Number of blocks.
# TYPE fsimage_path_blocks gauge
fsimage_path_blocks{path="/datalake/asset2",} 2.0
fsimage_path_blocks{path="/datalake/asset3",} 3.0
fsimage_path_blocks{path="/user/mm",} 0.0
fsimage_path_blocks{path="/datalake/asset1",} 0.0
# HELP fsimage_links Number of sym links.
# TYPE fsimage_links gauge
fsimage_links 0.0
# HELP fsimage_path_dirs Number of directories.
# TYPE fsimage_path_dirs gauge
fsimage_path_dirs{path="/datalake/asset2",} 0.0
fsimage_path_dirs{path="/datalake/asset3",} 2.0
fsimage_path_dirs{path="/user/mm",} 0.0
fsimage_path_dirs{path="/datalake/asset1",} 0.0
# HELP fsimage_fsize Overall file size distribution
# TYPE fsimage_fsize histogram
fsimage_fsize_bucket{le="0.0",} 0.0
fsimage_fsize_bucket{le="1048576.0",} 4.0
fsimage_fsize_bucket{le="3.3554432E7",} 13.0
fsimage_fsize_bucket{le="6.7108864E7",} 14.0
fsimage_fsize_bucket{le="1.34217728E8",} 15.0
fsimage_fsize_bucket{le="1.073741824E9",} 16.0
fsimage_fsize_bucket{le="1.073741824E10",} 16.0
fsimage_fsize_bucket{le="+Inf",} 16.0
fsimage_fsize_count 16.0
fsimage_fsize_sum 3.56409344E8
# HELP fsimage_path_set_links Number of sym links.
# TYPE fsimage_path_set_links gauge
fsimage_path_set_links{path_set="userMmAndFooAndAsset1",} 0.0
fsimage_path_set_links{path_set="datalakeAsset1and2",} 0.0
# HELP fsimage_dirs Number of directories.
# TYPE fsimage_dirs gauge
fsimage_dirs 14.0
# HELP fsimage_group_blocks Number of blocks.
# TYPE fsimage_group_blocks gauge
fsimage_group_blocks{group_name="root",} 1.0
fsimage_group_blocks{group_name="supergroup",} 13.0
fsimage_group_blocks{group_name="nobody",} 3.0
# HELP fsimage_blocks Number of blocks.
# TYPE fsimage_blocks gauge
fsimage_blocks 17.0
# HELP fsimage_scrape_duration_seconds Scrape duration
# TYPE fsimage_scrape_duration_seconds gauge
fsimage_scrape_duration_seconds 3.55993E-4
# HELP jvm_memory_bytes_used Used bytes of a given JVM memory area.
# TYPE jvm_memory_bytes_used gauge
jvm_memory_bytes_used{area="heap",} 2.816952E7
jvm_memory_bytes_used{area="nonheap",} 1.7311552E7
# HELP jvm_memory_bytes_committed Committed (bytes) of a given JVM memory area.
# TYPE jvm_memory_bytes_committed gauge
jvm_memory_bytes_committed{area="heap",} 1.28974848E8
jvm_memory_bytes_committed{area="nonheap",} 1.7956864E7
# HELP jvm_memory_bytes_max Max (bytes) of a given JVM memory area.
# TYPE jvm_memory_bytes_max gauge
jvm_memory_bytes_max{area="heap",} 1.908932608E9
jvm_memory_bytes_max{area="nonheap",} -1.0
# HELP jvm_memory_pool_bytes_used Used bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_bytes_used gauge
jvm_memory_pool_bytes_used{pool="Code Cache",} 2983360.0
jvm_memory_pool_bytes_used{pool="Metaspace",} 1.2829664E7
jvm_memory_pool_bytes_used{pool="Compressed Class Space",} 1498872.0
jvm_memory_pool_bytes_used{pool="PS Eden Space",} 2.816952E7
jvm_memory_pool_bytes_used{pool="PS Survivor Space",} 0.0
jvm_memory_pool_bytes_used{pool="PS Old Gen",} 0.0
# HELP jvm_memory_pool_bytes_committed Committed bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_bytes_committed gauge
jvm_memory_pool_bytes_committed{pool="Code Cache",} 3014656.0
jvm_memory_pool_bytes_committed{pool="Metaspace",} 1.3238272E7
jvm_memory_pool_bytes_committed{pool="Compressed Class Space",} 1703936.0
jvm_memory_pool_bytes_committed{pool="PS Eden Space",} 3.407872E7
jvm_memory_pool_bytes_committed{pool="PS Survivor Space",} 5242880.0
jvm_memory_pool_bytes_committed{pool="PS Old Gen",} 8.9653248E7
# HELP jvm_memory_pool_bytes_max Max bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_bytes_max gauge
jvm_memory_pool_bytes_max{pool="Code Cache",} 2.5165824E8
jvm_memory_pool_bytes_max{pool="Metaspace",} -1.0
jvm_memory_pool_bytes_max{pool="Compressed Class Space",} 1.073741824E9
jvm_memory_pool_bytes_max{pool="PS Eden Space",} 7.0516736E8
jvm_memory_pool_bytes_max{pool="PS Survivor Space",} 5242880.0
jvm_memory_pool_bytes_max{pool="PS Old Gen",} 1.431830528E9
# HELP fsimage_user_dirs Number of directories.
# TYPE fsimage_user_dirs gauge
fsimage_user_dirs{user_name="foo",} 0.0
fsimage_user_dirs{user_name="root",} 0.0
fsimage_user_dirs{user_name="mm",} 14.0
# HELP fsimage_load_file_size_bytes Size of raw FSImage
# TYPE fsimage_load_file_size_bytes gauge
fsimage_load_file_size_bytes 2420.0
# HELP fsimage_path_set_fsize Path set specific file size and file count
# TYPE fsimage_path_set_fsize summary
fsimage_path_set_fsize_count{path_set="userMmAndFooAndAsset1",} 3.0
fsimage_path_set_fsize_sum{path_set="userMmAndFooAndAsset1",} 6291456.0
fsimage_path_set_fsize_count{path_set="datalakeAsset1and2",} 2.0
fsimage_path_set_fsize_sum{path_set="datalakeAsset1and2",} 2098176.0
# HELP fsimage_user_replication Per user file replication
# TYPE fsimage_user_replication summary
fsimage_user_replication_count{user_name="root",} 1.0
fsimage_user_replication_sum{user_name="root",} 1.0
fsimage_user_replication_count{user_name="foo",} 1.0
fsimage_user_replication_sum{user_name="foo",} 1.0
fsimage_user_replication_count{user_name="mm",} 14.0
fsimage_user_replication_sum{user_name="mm",} 20.0
# HELP fsimage_group_fsize Per group file size and file count
# TYPE fsimage_group_fsize summary
fsimage_group_fsize_count{group_name="root",} 1.0
fsimage_group_fsize_sum{group_name="root",} 1024.0
fsimage_group_fsize_count{group_name="supergroup",} 13.0
fsimage_group_fsize_sum{group_name="supergroup",} 1.6766464E8
fsimage_group_fsize_count{group_name="nobody",} 2.0
fsimage_group_fsize_sum{group_name="nobody",} 1.8874368E8
# HELP fsimage_user_links Number of sym links.
# TYPE fsimage_user_links gauge
fsimage_user_links{user_name="foo",} 0.0
fsimage_user_links{user_name="root",} 0.0
fsimage_user_links{user_name="mm",} 0.0
# HELP fsimage_path_set_dirs Number of directories.
# TYPE fsimage_path_set_dirs gauge
fsimage_path_set_dirs{path_set="userMmAndFooAndAsset1",} 2.0
fsimage_path_set_dirs{path_set="datalakeAsset1and2",} 0.0
# HELP fsimage_group_links Number of sym links.
# TYPE fsimage_group_links gauge
fsimage_group_links{group_name="root",} 0.0
fsimage_group_links{group_name="supergroup",} 0.0
fsimage_group_links{group_name="nobody",} 0.0
# HELP fsimage_load_duration_seconds Time for loading/parsing FSImage
# TYPE fsimage_load_duration_seconds summary
fsimage_load_duration_seconds_count 1.0
fsimage_load_duration_seconds_sum 0.109007164
# HELP fsimage_scrape_errors_total Counts failed scrapes.
# TYPE fsimage_scrape_errors_total counter
fsimage_scrape_errors_total 0.0
# HELP fsimage_path_set_blocks Number of blocks.
# TYPE fsimage_path_set_blocks gauge
fsimage_path_set_blocks{path_set="userMmAndFooAndAsset1",} 3.0
fsimage_path_set_blocks{path_set="datalakeAsset1and2",} 2.0
# HELP fsimage_group_dirs Number of directories.
# TYPE fsimage_group_dirs gauge
fsimage_group_dirs{group_name="root",} 0.0
fsimage_group_dirs{group_name="supergroup",} 14.0
fsimage_group_dirs{group_name="nobody",} 0.0
# HELP fsimage_replication Overall file replication
# TYPE fsimage_replication summary
fsimage_replication_count 16.0
fsimage_replication_sum 22.0
# HELP fsimage_user_blocks Number of blocks.
# TYPE fsimage_user_blocks gauge
fsimage_user_blocks{user_name="foo",} 2.0
fsimage_user_blocks{user_name="root",} 1.0
fsimage_user_blocks{user_name="mm",} 14.0
# HELP fsimage_exporter_app_info Application build info
# TYPE fsimage_exporter_app_info gauge
fsimage_exporter_app_info{appName="fsimage_exporter",appVersion="1.1-SNAPSHOT",buildTime="2017-10-17/19:13",buildScmVersion="491d70f88c6bc96c6a0d19fca27af07519534782",buildScmBranch="master",} 1.0
# HELP fsimage_scrape_requests_total Exporter requests made
# TYPE fsimage_scrape_requests_total counter
fsimage_scrape_requests_total 2.0
# HELP fsimage_path_links Number of sym links.
# TYPE fsimage_path_links gauge
fsimage_path_links{path="/datalake/asset2",} 0.0
fsimage_path_links{path="/datalake/asset3",} 0.0
fsimage_path_links{path="/user/mm",} 0.0
fsimage_path_links{path="/datalake/asset1",} 0.0
```
## License

This Hadoop HDFS FSImage Exporter is released under the [Apache 2.0 license](LICENSE).

```
Copyright 2018 Marcel May

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
