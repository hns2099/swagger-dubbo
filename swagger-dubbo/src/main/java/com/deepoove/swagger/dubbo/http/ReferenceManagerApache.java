package com.deepoove.swagger.dubbo.http;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ServiceBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;


public class ReferenceManagerApache implements IRefrenceManager {
    
    private static Logger logger = LoggerFactory.getLogger(ReferenceManagerApache.class);

    @SuppressWarnings("rawtypes")
    private static Collection<ServiceBean> services;

    private static Map<Class<?>, Object> interfaceMapProxy = new ConcurrentHashMap<Class<?>, Object>();
    private static Map<Class<?>, Object> interfaceMapRef = new ConcurrentHashMap<Class<?>, Object>();

    private static ApplicationConfig application;
    private ApplicationContext applicationContext;

    public ReferenceManagerApache(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public synchronized void init() {
        if (services != null && !services.isEmpty()) {
            return ;
        }
        services = new HashSet<ServiceBean>();
        try {
            services.addAll(this.applicationContext.getBeansOfType(ServiceBean.class).values());
        } catch (Exception e) {
            logger.error("Get All Dubbo Service Error", e);
        }
        for (ServiceBean<?> bean : services) {
            interfaceMapRef.putIfAbsent(bean.getInterfaceClass(), bean.getRef());
        }
        
        //
        if (!services.isEmpty()) {
			ServiceBean<?> bean = services.toArray(new ServiceBean[]{})[0];
			application = bean.getApplication();
        }
        
    }

    @Override
    public Object getProxy(String interfaceClass) {
        init();
        Set<Entry<Class<?>, Object>> entrySet = interfaceMapProxy.entrySet();
        for (Entry<Class<?>, Object> entry : entrySet) {
            if (entry.getKey().getName().equals(interfaceClass)) { return entry.getValue(); }
        }

        for (ServiceBean<?> service : services) {
            if (interfaceClass.equals(service.getInterfaceClass().getName())) {
                ReferenceConfig<Object> reference = new ReferenceConfig<Object>();
                reference.setApplication(service.getApplication());
                reference.setRegistry(service.getRegistry());
                reference.setRegistries(service.getRegistries());
                reference.setInterface(service.getInterfaceClass());
                reference.setVersion(service.getVersion());
                interfaceMapProxy.put(service.getInterfaceClass(), reference.get());
                return reference.get();
            }
        }
        return null;
    }

    @Override
    public Entry<Class<?>, Object> getRef(String interfaceClass) {
        init();
        Set<Entry<Class<?>, Object>> entrySet = interfaceMapRef.entrySet();
        for (Entry<Class<?>, Object> entry : entrySet) {
            if (entry.getKey().getName().equals(interfaceClass)) { return entry; }
        }
        return null;
    }

    @Override
    public Collection<?> getServices() {
        init();
        return services;
    }

    @Override
    public Object getApplication() {
        init();
        return application;
    }

    @Override
    public Map<Class<?>, Object> getInterfaceMapRef() {
        init();
        return interfaceMapRef;
    }

}
