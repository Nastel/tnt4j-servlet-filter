# TrackingFilter
Simple end-user monitoring for Java EE web applications.

TrackingFilter is an implementation of Java EE HTTP Servlet Filter. This servlet filter tracks the following:
* End-user performance, behavior
* HTTP request performance, errors
* HTTP header, request parameters and attributes
* JVM context such as CPU, memory, GC

The output can be streamed to any of the TNT4J sinks: file, log4j, socket, visual analysis using jkoolcloud.com (http://www.jkoolcloud.com).

## Adding Tracking to your web applications
Simply add the following to your application's `web.xml`:
```xml
<filter>
	<filter-name>TrackingFilter</filter-name>
	<filter-class>org.tnt4j.servlet.filter.TrackingFilter</filter-class>
</filter>
<filter-mapping>
	<filter-name>TrackingFilter</filter-name>
	<url-pattern>/*</url-pattern>
</filter-mapping>
<context-param>
	<param-name>op-level</param-name>
	<param-value>DEBUG</param-value>
</context-param>
```
<b>NOTE:</b> Make sure all jar files required by TrackingFilter are in your application's classpath.

# Project Dependencies
TrackingFilter requires the following:
* JDK 1.6+
* TNT4J (https://github.com/Nastel/TNT4J)

# Available Integrations
* TNT4J (https://github.com/Nastel/TNT4J)
* Log4J (http://logging.apache.org/log4j/1.2/)
* jkoolcloud.com (https://www.jkoolcloud.com)
* AutoPilot M6 (http://www.nastel.com/products/autopilot-m6.html)
