package org.glassfish.jersey.logging;

import cd.connect.context.ConnectContext;
import cd.connect.jersey.common.logging.JerseyFiltering;
import org.glassfish.jersey.message.MessageUtils;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The majority of this is taken from the Jersey Logger itself, with a few minor changes for Connect uses.
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@ConstrainedTo(RuntimeType.SERVER)
@Priority(Integer.MAX_VALUE)
@Singleton
@SuppressWarnings("ClassWithMultipleLoggers")
public class FilteringServerLoggingFilter extends BaseFilteringLogger implements ContainerRequestFilter, ContainerResponseFilter {

	/*
	 * Create a logging filter with custom logger and custom settings of entity
	 * logging.
	 */
	@Inject
	public FilteringServerLoggingFilter(JerseyFiltering jerseyFiltering) {
		super(jerseyFiltering);
	}

	protected void recordIncoming(ContainerRequestContext context, String action) {
		// icky cat of fields but its the fastest was to tdo it.
		ConnectContext.set(Constants.REST_CONTEXT, action + ": " + context.getMethod() + " - " + context.getUriInfo().getRequestUri().toASCIIString());
	}

	// incoming request
	@Override
	public void filter(final ContainerRequestContext context) throws IOException {

		String uri = context.getUriInfo().getRequestUri().getPath();
		if (jerseyFiltering.excludeForUri(uri)) {
			return;
		}

		long id = _id.incrementAndGet();
		context.setProperty(LOGGING_ID_PROPERTY, id);

		recordIncoming(context, "received");

		StringBuilder b = new StringBuilder();
		printRequestLine(b, "Server has received a request", id, context.getMethod(), context.getUriInfo().getRequestUri());
		printPrefixedHeaders(b, id, REQUEST_PREFIX, context.getHeaders());

		if (context.hasEntity() && printEntity(verbosity, context.getMediaType()) && !jerseyFiltering.excludePayloadForUri(uri)) {
			context.setEntityStream(logInboundEntity(b, context.getEntityStream(), MessageUtils.getCharset(context.getMediaType())));
		}

		log(b);
	}

	@Override
	public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) throws IOException {

		String uri = requestContext.getUriInfo().getRequestUri().getPath();
		if (jerseyFiltering.excludeForUri(uri)) {
			return;
		}

		recordIncoming(requestContext, "responded");
		ConnectContext.set(Constants.REST_STATUS_CODE, Integer.toString(responseContext.getStatus()));

		Object requestId = requestContext.getProperty(LOGGING_ID_PROPERTY);
		long id = requestId != null ? (Long) requestId : _id.incrementAndGet();

		StringBuilder b = new StringBuilder();

		printResponseLine(b, "Server responded with a response", id, responseContext.getStatus());
		printPrefixedHeaders(b, id, RESPONSE_PREFIX, responseContext.getStringHeaders());

		if (responseContext.hasEntity() && printEntity(verbosity, responseContext.getMediaType())) {
			OutputStream stream = new BaseFilteringLogger.LoggingStream(b, responseContext.getEntityStream());
			responseContext.setEntityStream(stream);
			requestContext.setProperty(ENTITY_LOGGER_PROPERTY, stream);
			// not calling log(b) here - it will be called by the interceptor
		} else {
			log(b);
		}
	}

}
