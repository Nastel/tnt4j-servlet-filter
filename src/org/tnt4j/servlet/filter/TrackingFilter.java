/*
 * Copyright 2014 Nastel Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tnt4j.servlet.filter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import com.nastel.jkool.tnt4j.TrackingLogger;
import com.nastel.jkool.tnt4j.core.OpLevel;
import com.nastel.jkool.tnt4j.core.OpType;
import com.nastel.jkool.tnt4j.core.Snapshot;
import com.nastel.jkool.tnt4j.tracker.TrackingActivity;
import com.nastel.jkool.tnt4j.tracker.TrackingEvent;
import com.nastel.jkool.tnt4j.utils.Utils;

public class TrackingFilter implements Filter {
	public static final String SNAPSHOT_PARMS = "Parameters";
	public static final String SNAPSHOT_ATTRS = "Attributes";
	
	public static final String CORRID_SESSION_ID = "session-id";
	public static final String TAG_URI_QUERY = "uri-query";
	public static final String USER_REMOTE = "user-remote";
	public static final String MSG_HTTP_HEADER = "http-header";

	public static final String CORRID_KEY = "corr-key";
	public static final String TAG_KEY = "tag-key";
	public static final String USER_KEY = "user-key";
	public static final String MSG_KEY = "msg-key";
	public static final String OPLEVEL_KEY = "op-level";

	TrackingLogger logger;
	String corrKey = CORRID_KEY;
	String tagKey = TAG_KEY;
	String userKey = USER_KEY;
	String msgKey = MSG_KEY;
	OpLevel level = OpLevel.INFO;

	@Override
	public void init(FilterConfig config) throws ServletException {
		String source = config.getFilterName();
		corrKey = config.getInitParameter(CORRID_KEY);
		corrKey = corrKey == null ? CORRID_SESSION_ID : corrKey;

		tagKey = config.getInitParameter(TAG_KEY);
		tagKey = tagKey == null ? TAG_URI_QUERY : tagKey;
		
		userKey = config.getInitParameter(USER_KEY);
		userKey = userKey == null ? USER_REMOTE : userKey;
		
		msgKey = config.getInitParameter(MSG_KEY);
		msgKey = msgKey == null ? MSG_HTTP_HEADER : msgKey;
		
		String levelString = config.getInitParameter(OPLEVEL_KEY);
		level = levelString != null ? OpLevel.valueOf(levelString) : level;
		logger = TrackingLogger.getInstance(source);
	}

	@Override
	public void destroy() {
		logger.close();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
	        ServletException {
		Throwable error = null;
		TrackingActivity activity = null;
		TrackingEvent httpEvent = null;
		HttpServletResponseWrapper httpResp = null;
		try {
			long beginUsec = Utils.currentTimeUsec();
			if (request instanceof HttpServletRequest) {
				HttpServletRequest httpReq = (HttpServletRequest) request;

				// start activity and capture its state
				activity = logger.newActivity(level, httpReq.getContextPath());
				activity.start(beginUsec);
				activity.setLocation(httpReq.getRemoteAddr());
				activity.setResource(httpReq.getRequestURI());
				httpEvent = captureRequest(httpReq, activity);
				httpEvent.start(activity.getStartTime());
				String username = getUserName(httpReq);
				if (username != null) {
					activity.setUser(username);
					httpEvent.getOperation().setUser(username);
				}
			}
			if (response instanceof HttpServletResponse) {
				httpResp = new HttpServletResponseWrapper((HttpServletResponse)response);
				chain.doFilter(request, httpResp);
			} else {
				chain.doFilter(request, response);
			}
		} catch (Throwable ex) {
			error = ex;
			throw ex;
		} finally {
			endTiming(request, httpResp, activity, httpEvent, error);
		}
	}

	protected void endTiming(ServletRequest request, HttpServletResponseWrapper httpResp,
	        TrackingActivity activity, TrackingEvent httpEvent, Throwable error) {
		try {
			if (activity != null) {
				if (httpResp != null) {
					activity.setReasonCode(httpResp.getStatus());
					httpEvent.getOperation().setReasonCode(httpResp.getStatus());
				}
				httpEvent.stop(error);
				activity.tnt(httpEvent);
				endAndEnrich((HttpServletRequest)request, activity, error);
				logger.tnt(activity);
			}
		} catch (Throwable ex) {
			logger.log(OpLevel.ERROR, "Failed to time event", ex);
		}
	}

	protected void endAndEnrich(HttpServletRequest httpReq, TrackingActivity activity, Throwable error) {
		Map<String, String[]> pMap = httpReq.getParameterMap();
		Snapshot parms = logger.newSnapshot(httpReq.getRequestURI(), SNAPSHOT_PARMS);
		for (Entry<String, String[]> entry: pMap.entrySet()) {
			String [] list = entry.getValue();
			if (list != null && list.length > 0) {
				parms.add(entry.getKey(), list[0]);
			}
		}
		
		Snapshot attrs = logger.newSnapshot(httpReq.getRequestURI(), SNAPSHOT_ATTRS);
		Enumeration<String> enList = httpReq.getAttributeNames();
		while (enList.hasMoreElements()) {
			String key = enList.nextElement();
			attrs.add(key, httpReq.getAttribute(key));
		}
		activity.stop(error);
		if (parms.size() > 0) activity.addSnapshot(parms);
		if (attrs.size() > 0) activity.addSnapshot(attrs);
	}
	
	protected TrackingEvent captureRequest(HttpServletRequest httpReq, TrackingActivity activity) {
		String corrid = getCorrId(httpReq);
		String msgTag = getMsgTag(httpReq);	
		TrackingEvent httpEvent = logger.newEvent(activity.getSeverity(),
				OpType.REQUEST,
		        httpReq.getMethod() + httpReq.getRequestURI(),
		        corrid,
		        msgTag,
		        getMsgBody(httpReq));
		httpEvent.setLocation(httpReq.getRemoteAddr());
		httpEvent.getOperation().setResource(httpReq.getRequestURI());
		return httpEvent;
	}

	protected String getMsgBody(HttpServletRequest httpReq) {
		String msgText = null;
		if (msgKey.equalsIgnoreCase(MSG_HTTP_HEADER)) {
			msgText = getMessage(httpReq);
		} else {
			msgText = httpReq.getParameter(msgKey);	
		}		
		return msgText;
	}
	
	protected String getUserName(HttpServletRequest httpReq) {
		String username = null;
		if (userKey.equalsIgnoreCase(USER_REMOTE)) {
			username = httpReq.getRemoteUser();
		} else {
			username = httpReq.getParameter(userKey);	
		}		
		return username;
	}
	
	protected String getCorrId(HttpServletRequest httpReq) {
		String corrid = null;		
		if (corrKey.equalsIgnoreCase(CORRID_SESSION_ID)) {
			corrid = httpReq.getSession().getId();
		} else {
			corrid = httpReq.getParameter(corrKey);			
		}
		return corrid;
	}
	
	protected String getMsgTag(HttpServletRequest httpReq) {
		String msgTag = null;		
		if (tagKey.equalsIgnoreCase(TAG_URI_QUERY)) {
			String queryString = httpReq.getQueryString();
			msgTag = queryString != null? httpReq.getRequestURI() + "?" + queryString: httpReq.getRequestURI();
		} else {
			msgTag = httpReq.getParameter(tagKey);			
		}
		return msgTag;
	}
	
	protected static Map<String, String> splitQuery(String query) {
		if (query == null) return null;
		Map<String, String> query_pairs = new LinkedHashMap<String, String>(49);
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			try {
				query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
				        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
			}
		}
		return query_pairs;
	}

	protected String getMessage(HttpServletRequest request2) {
		StringBuilder msg = new StringBuilder(512);
		msg.append(request2.getMethod()).append(" ").append(request2.getRequestURI());
		if (request2.getQueryString() != null) {
			msg.append(request2.getQueryString());
		}
		msg.append("\r\n");
		Enumeration<?> names = request2.getHeaderNames();
		while (names.hasMoreElements()) {
			String hdName = String.valueOf(names.nextElement());
			msg.append(hdName).append(": ").append(request2.getHeader(hdName)).append("\r\n");
		}
		return msg.toString();
	}
}
