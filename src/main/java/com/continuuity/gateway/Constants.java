package com.continuuity.gateway;

import com.continuuity.data.operation.OperationContext;

/**
 * Constants is a utility class that contains a set of universal constants
 * that are used throughout the Gateway project.
 */
public class Constants {

  /**
   * The prefix for all continuity classes
   */
  static final String CONTINUUITY_PREFIX = "com.continuuity.";

  /**
   * The prefix for all gateway properties
   */
  static final String GATEWAY_PREFIX = "gateway.";

  /**
   * Used by the external client to identify and authenticate the client
   */
  public static final String HEADER_STREAM_CONSUMER
      = CONTINUUITY_PREFIX + "stream.consumer";

  /**
   * Used by the external client to indicate what end point an event goes to
   */
  public static final String HEADER_DESTINATION_STREAM
      = CONTINUUITY_PREFIX + "destination";

  /**
   * Created by gateway to annotate each event with the name of the collector
   * through which it was ingested
   */
  public static final String HEADER_FROM_COLLECTOR
      = CONTINUUITY_PREFIX + "collector";

  /**
   * List of named collectors for the gateway
   */
  public static final String CONFIG_CONNECTORS
      = GATEWAY_PREFIX + "connectors";

  /**
   * Hostname of the gateway service
   */
  public static final String CONFIG_HOSTNAME
      = GATEWAY_PREFIX + "hostname";

  /**
   * Whether gateway should provide ZK service discovery to the connectors
   */
  public static final String CONFIG_DO_SERVICE_DISCOVERY
      = GATEWAY_PREFIX + "discovery";

  /**
   * Whether gateway should authenticate requests.
   */
  public static final String CONFIG_AUTHENTICATION_REQUIRED
      = GATEWAY_PREFIX + "authenticate";

  /**
   * Default setting for whether gateway should authenticate (false for local).
   */
  public static final boolean CONFIG_AUTHENTICATION_REQUIRED_DEFAULT = false;

  /**
   * Configuration parameter name for setting the cluster name.
   */
  public static final String CONFIG_CLUSTER_NAME
      = GATEWAY_PREFIX + "cluster.name";

  /**
   * Default cluster name.
   */
  public static final String CONFIG_CLUSTER_NAME_DEFAULT = "localhost";

  /**
   * Class name of a named connector
   */
  public static final String CONFIG_CLASSNAME = "class";

  /**
   * Port number of a connector
   */
  public static final String CONFIG_PORT = "port";

  /**
   * Number of worker threads for a connector
   */
  public static final String CONFIG_THREADS = "threads";

  /**
   * Whether an HTTP connector supports chunked requests
   */
  public static final String CONFIG_CHUNKING = "chunk";

  /**
   * The maximal supported size of the content of an Http request
   */
  public static final String CONFIG_MAX_SIZE = "maxsize";

  /**
   * Whether an HTTP connector supports SSL
   */
  public static final String CONFIG_SSL = "ssl";

  /**
   * Path prefix in an HTTP URL.
   * For instance, in http://g.d.c/rest/destination/myStream the prefix
   * is "/rest"
   */
  public static final String CONFIG_PATH_PREFIX = "prefix";

  /**
   * Middle component in the HTTP URL
   * For instance, in http://g.d.c/rest/destination/myStream the middle is
   * /destination/
   */
  public static final String CONFIG_PATH_MIDDLE = "middle";

  /**
   * Generate the name of a property option for a named Collector. Basically
   * a helper method that concatenates some strings.
   *
   * @param collectorName The name of the Collector
   * @param propertyName  The name of the property
   * @return A fully composed collector configuration property
   */
  public static String buildConnectorPropertyName(String collectorName,
                                                  String propertyName) {

    return collectorName + "." + propertyName;
  }

  public static boolean isContinuuityHeader(String header) {
    return header.startsWith(CONTINUUITY_PREFIX);
  }

  // Defaults for various configurations

  /**
   * Default number of worker threads for a connector
   */
  public static final int DEFAULT_THREADS = 20;

  public static final String metricsServiceName =
      com.continuuity.common.conf.Constants.SERVICE_METRICS_FRONTEND_SERVER;
  public static final String flowServiceName =
      com.continuuity.common.conf.Constants.SERVICE_FLOW_SERVER;
} // end of Constants class
