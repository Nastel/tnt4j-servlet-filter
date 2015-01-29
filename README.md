# TrackingFilter
Simple end-user monitoring for JEE web applications.

TrackingFilter is JEE HTTP Servlet Tracking Filter. This servlet filter tracks the following:
* End-user performance, behavior
* HTTP request performance, errors
* HTTP header, request parameters and attributes
* JVM context such as CPU, memory, GC

The output can be streamed to any of the TNT4J sinks: file, log4j, socket, visual analysis using jkoolcloud.com (http://www.jkoolcloud.com).

# Project Dependencies
TrackingFilter requires the following:
* JDK 1.6+
* TNT4J (https://github.com/Nastel/TNT4J)

# Available Integrations
* TNT4J (https://github.com/Nastel/TNT4J)
* Log4J (http://logging.apache.org/log4j/1.2/)
* jkoolcloud.com (https://www.jkoolcloud.com)
* AutoPilot M6 (http://www.nastel.com/products/autopilot-m6.html)
