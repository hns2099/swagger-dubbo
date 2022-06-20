package com.deepoove.swagger.dubbo.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.deepoove.swagger.dubbo.config.SwaggerDubboProperties;
import com.deepoove.swagger.dubbo.http.HttpMatch;
import com.deepoove.swagger.dubbo.http.IRefrenceManager;
import com.deepoove.swagger.dubbo.reader.NameDiscover;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.swagger.annotations.Api;
import io.swagger.util.Json;
import io.swagger.util.PrimitiveType;

@Controller
@RequestMapping("${swagger.dubbo.http:h}")
@Api(hidden = true)
@ConditionalOnClass(name = "org.apache.dubbo.config.spring.ServiceBean")
public class DubboHttpController {

    @Autowired
    IRefrenceManager refrenceManager;

	private static Logger logger = LoggerFactory.getLogger(DubboHttpController.class);

	@Autowired
	SwaggerDubboProperties swaggerDubboConfig;

	@RequestMapping(value = "/{interfaceClass}/{methodName}", produces = "application/json; charset=utf-8")
	@ResponseBody
	public ResponseEntity<String> invokeDubbo(@PathVariable("interfaceClass") String interfaceClass,
			@PathVariable("methodName") String methodName, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		return invokeDubbo(interfaceClass, methodName, null, request, response);
	}

	@RequestMapping(value = "/{interfaceClass}/{methodName}/{operationId}", produces = "application/json; charset=utf-8")
	@ResponseBody
	public ResponseEntity<String> invokeDubbo(@PathVariable("interfaceClass") String interfaceClass,
			@PathVariable("methodName") String methodName,
			@PathVariable("operationId") String operationId, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		if (!swaggerDubboConfig.isEnable()) { return new ResponseEntity<String>(HttpStatus.NOT_FOUND); }

		Object ref = null;
		Method method = null;
		Object result = null;

		Entry<Class<?>, Object> entry = refrenceManager.getRef(interfaceClass);

		if (null == entry){
		    logger.info("No Ref Service FOUND.");
		    return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
		ref = entry.getValue();
		HttpMatch httpMatch = new HttpMatch(entry.getKey(), ref.getClass());
		Method[] interfaceMethods = httpMatch.findInterfaceMethods(methodName);

		if (null != interfaceMethods && interfaceMethods.length > 0) {
			Method[] refMethods = httpMatch.findRefMethods(interfaceMethods, operationId,
					request.getMethod());
			method = httpMatch.matchRefMethod(refMethods, methodName, request.getParameterMap().keySet());
		}
		if (null == method) {
		    logger.info("No Service Method FOUND.");
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
		String[] parameterNames = NameDiscover.parameterNameDiscover.getParameterNames(method);

		logger.info("[Swagger-dubbo] Invoke by " + swaggerDubboConfig.getCluster());
		if (SwaggerDubboProperties.CLUSTER_RPC.equals(swaggerDubboConfig.getCluster())){
    		ref = refrenceManager.getProxy(interfaceClass);
    		if (null == ref){
    		    logger.info("No Ref Proxy Service FOUND.");
                return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    		}
    		method = ref.getClass().getMethod(method.getName(), method.getParameterTypes());
    		if (null == method) {
    		    logger.info("No Proxy Service Method FOUND.");
                return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
            }
		}
		logger.debug("[Swagger-dubbo] Invoke dubbo service method:{},parameter:{}", method, Json.pretty(request.getParameterMap()));
		if (null == parameterNames || parameterNames.length == 0) {
			result = method.invoke(ref);
		} else {
			Object[] args = new Object[parameterNames.length];
			String requestBodyStr = getStrFromHttpBody(request);
			Type[] parameterTypes = method.getGenericParameterTypes();
			Class<?>[] parameterClazz = method.getParameterTypes();

			for (int i = 0; i < parameterNames.length; i++) {
				String paramValue = null;
				if(parameterNames.length == 1 && !"".equals(requestBodyStr)){
					paramValue = requestBodyStr;
				}
				else{
					paramValue = request.getParameter(parameterNames[i]);
				}
				Object suggestPrameterValue = suggestPrameterValue(parameterTypes[i],
						parameterClazz[i], paramValue);
				args[i] = suggestPrameterValue;
			}
			result = method.invoke(ref, args);
		}
		return ResponseEntity.ok(Json.mapper().writeValueAsString(result));
	}

    private Object suggestPrameterValue(Type type, Class<?> cls, String parameter)
			throws JsonParseException, JsonMappingException, IOException {
		PrimitiveType fromType = PrimitiveType.fromType(type);
		if (null != fromType) {
			DefaultConversionService service = new DefaultConversionService();
			boolean actual = service.canConvert(String.class, cls);
			if (actual) { return service.convert(parameter, cls); }
		} else {
			if (null == parameter) return null;
            try {
                return Json.mapper().readValue(parameter, cls);
            } catch (Exception e) {
                throw new IllegalArgumentException("The parameter value [" + parameter + "] should be json of [" + cls.getName() + "] Type.", e);
            }
		}
		try {
			return Class.forName(cls.getName()).newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private String getStrFromHttpBody(HttpServletRequest request) {

		String str;
		StringBuilder wholeStr = new StringBuilder();
		if(request != null){
			try {
				BufferedReader br = request.getReader();
				while((str = br.readLine()) != null){
					wholeStr.append(str);
				}
			} catch (IOException e) {
			}
		}
		return wholeStr.toString();
	}

}
