package org.bimwebserver.servlets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.bimserver.shared.ConvertException;
import org.bimserver.shared.JsonConverter;
import org.bimserver.shared.NotificationInterface;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.shared.meta.SMethod;
import org.bimserver.shared.meta.SParameter;
import org.bimserver.shared.meta.SService;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONTokener;

import com.google.common.base.Charsets;

public class NotificationServlet extends HttpServlet {

	private JsonConverter converter;
	private HashMap<String, SService> sServices = new HashMap<String, SService>();
	private HashMap<String, Object> services = new HashMap<String, Object>();

	public NotificationServlet() {
		sServices.put(NotificationInterface.class.getSimpleName(), new SService(null, NotificationInterface.class));
		converter = new JsonConverter(sServices);
	}
	
	@Override
	public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
		response.setHeader("Access-Control-Allow-Headers", "Content-Type");
		response.setHeader("Content-Type", "text/html");
		handleRequest(request, response);
	}

	private void handleRequest(HttpServletRequest httpRequest, HttpServletResponse response) {
		try {
			ServletInputStream inputStream = httpRequest.getInputStream();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			IOUtils.copy(inputStream, outputStream);
			JSONObject request = new JSONObject(new JSONTokener(new String(outputStream.toByteArray(), Charsets.UTF_8)));
			JSONObject requestObject = request.getJSONObject("request");

			response.setHeader("Content-Type", "application/json");
			
			String interfaceName = requestObject.getString("interface");
			String methodName = requestObject.getString("method");
			SMethod method = sServices.get(NotificationInterface.class.getSimpleName()).getSMethod(methodName);
			if (method == null) {
				throw new UserException("Method " + methodName + " not found on " + interfaceName);
			}
			Object[] parameters = new Object[method.getParameters().size()];
			for (int i=0; i<method.getParameters().size(); i++) {
				SParameter parameter = method.getParameter(i);
				try {
					if (requestObject.has(parameter.getName())) {
						parameters[i] = converter.fromJson(parameter.getType(), requestObject.get(parameter.getName()));
					}
				} catch (ConvertException e) {
					e.printStackTrace();
				}
			}

			try {
				Object result = method.invoke(services.get(interfaceName), parameters);
				JSONObject responseObject = new JSONObject();
				responseObject.put("response", converter.toJson(result));
				responseObject.write(response.getWriter());
			} catch (Exception e) {
				sendException(response, e);
			}
		} catch (Exception e) {
			sendException(response, e);
		}
	}

	private void sendException(HttpServletResponse response, Exception exception) {
		try {
			JSONObject responseObject = new JSONObject();
			JSONObject exceptionJson = new JSONObject();
			exceptionJson.put("message", exception.getMessage());
			responseObject.put("exception", exceptionJson);
			responseObject.write(response.getWriter());
		} catch (JSONException e) {
		} catch (IOException e) {
		}
	}
}